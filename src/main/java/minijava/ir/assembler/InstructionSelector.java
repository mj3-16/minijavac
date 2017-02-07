package minijava.ir.assembler;

import static firm.bindings.binding_irnode.ir_opcode.iro_Block;
import static firm.bindings.binding_irnode.ir_opcode.iro_Cmp;
import static firm.bindings.binding_irnode.ir_opcode.iro_Return;
import static minijava.ir.utils.FirmUtils.modeToWidth;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.sun.jna.Platform;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.Relation;
import firm.nodes.Block;
import firm.nodes.Cmp;
import firm.nodes.Cond;
import firm.nodes.End;
import firm.nodes.Jmp;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Phi;
import firm.nodes.Proj;
import firm.nodes.Return;
import firm.nodes.Start;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.CodeBlock.ExitArity;
import minijava.ir.assembler.block.CodeBlock.ExitArity.One;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.Enter;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.instructions.Leave;
import minijava.ir.assembler.instructions.Mov;
import minijava.ir.assembler.instructions.Test;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.MethodInformation;
import minijava.ir.utils.NodeUtils;
import minijava.ir.utils.ProjPair;

public class InstructionSelector extends NodeVisitor.Default {

  private boolean retainControlFlow = true;
  private final Graph graph;
  private final ActivationRecord activationRecord;
  private final VirtualRegisterMapping mapping = new VirtualRegisterMapping();
  private final BiMap<Block, CodeBlock> blocks = HashBiMap.create();
  private final Map<Block, Cmp> lastCmp = new HashMap<>();
  private final Set<Node> retainedComputations = new LinkedHashSet<>();
  private final TreeMatcher matcher = new TreeMatcher(mapping);

  private InstructionSelector(Graph graph, ActivationRecord activationRecord) {
    this.graph = graph;
    this.activationRecord = activationRecord;
  }

  @Override
  public void defaultVisit(Node node) {
    if (node.getMode().equals(Mode.getM())) {
      // Memory edges did their job at keeping side-effects in order and kann just be erased now.
      return;
    }

    // Determine if we really have to generate an intermediate value in a register for this.

    if (!usedMultipleTimes(node)
        && !usedInSuccessorBlock(node)
        && !neededInRegister(node)
        && !retainedComputations.contains(node)) {
      // We don't handle these cases here, as the node matcher does not need to put intermediate
      // results in registers.
      return;
    }

    // Otherwise we are 'unlucky' and have to produce code for the subtree at node.
    List<Instruction> newInstructions = matcher.match(node);
    CodeBlock block = getCodeBlockOfNode(node);
    block.instructions.addAll(newInstructions);
  }

  private CodeBlock getCodeBlockOfNode(Node node) {
    return getCodeBlock((Block) node.getBlock());
  }

  private CodeBlock getCodeBlock(Block block) {
    return blocks.computeIfAbsent(block, b -> new CodeBlock(getLabelForBlock(b)));
  }

  private static String getLabelForBlock(Block block) {
    Graph definingGraph = block.getGraph();
    String ldName = new MethodInformation(definingGraph).ldName;
    if (definingGraph.getStartBlock().equals(block)) {
      return ldName;
    }
    String ldFormat;
    if (Platform.isLinux()) {
      ldFormat = ".L%d_%s";
    } else {
      ldFormat = "L%d_%s";
    }
    return String.format(ldFormat, block.getNr(), ldName);
  }

  private static boolean usedInSuccessorBlock(Node node) {
    BackEdges.Edge usage = Iterables.getOnlyElement(BackEdges.getOuts(node));
    assert node.getOpCode() != iro_Block;
    return !node.getBlock().equals(usage.node.getBlock());
  }

  private static boolean usedMultipleTimes(Node node) {
    return BackEdges.getNOuts(node) > 1;
  }

  private static boolean neededInRegister(Node node) {
    // For cases like Return, which we handle in InstructionSelector. This implies we need its
    // operand in a VirtualRegister (which might be erased again later).
    return seq(BackEdges.getOuts(node))
        .filter(be -> be.node.getOpCode() == iro_Return)
        .isNotEmpty();
  }

  @Override
  public void visit(Block node) {
    // All book-keeping will be handled as we go
  }

  @Override
  public void visit(Phi phi) {
    if (phi.getMode().equals(Mode.getM())) {
      // Memory edges did their job at keeping side-effects in order and kann just be erased now.
      return;
    }

    // For the sake of breaking cycles we will note the register values we are interested in
    // and do nothing except for noting the Phi in its code-block.
    // SSA form deconstruction happens after/while register allocation.
    VirtualRegister result = mapping.registerForNode(phi);
    Map<CodeBlock, VirtualRegister> args =
        seq(phi.getPreds()).toMap(this::getCodeBlockOfNode, mapping::registerForNode);
    OperandWidth width = modeToWidth(phi.getMode());
    CodeBlock block = getCodeBlockOfNode(phi);
    block.phis.add(new PhiFunction(args, result, width, phi));
  }

  @Override
  public void visit(Cond cond) {
    if (retainControlFlow) {
      // Control flow has to be the last group of instructions of a block, so we handle it
      // in a second pass after all other computations.
      retainedComputations.add(cond);
      return;
    }
    Block irBlock = (Block) cond.getBlock();
    CodeBlock block = getCodeBlock(irBlock);
    // We rely on the topological ordering also present in retainedComputations and assume
    // that the Cmp we match on is still visible through the flags register, if the Cond is
    // in the same block as the Cmp.
    Node sel = cond.getSelector();
    Relation relation; // Needed for the conditional jump.
    assert sel.getOpCode() != iro_Cmp
        || !irBlock.equals(sel.getBlock())
        || sel.equals(lastCmp.get(irBlock));
    boolean flagsStillSet = sel.getOpCode() == iro_Cmp && sel.equals(lastCmp.get(irBlock));
    if (!flagsStillSet) {
      // i.o.w.: sel is not a Cmp or is in another block
      assert sel.getOpCode() != iro_Cmp || !irBlock.equals(sel.getBlock());
      // In this case we have to rematerialize the flags register with the node's value.
      VirtualRegister selResult = mapping.registerForNode(sel);
      OperandWidth width = modeToWidth(Mode.getb());
      RegisterOperand op = new RegisterOperand(width, selResult);
      block.instructions.add(new Test(op, op));
      relation = Relation.LessGreater; // Should output in a jnz/jne
    } else {
      Cmp cmp = (Cmp) sel;
      relation = cmp.getRelation();
    }

    ProjPair projs = NodeUtils.determineProjectionNodes(cond).get();
    Block falseTarget = getJumpTarget(projs.false_);
    Block trueTarget = getJumpTarget(projs.true_);
    // We don't generate jump instructions here. They aren't generated until we really translate
    // the code blocks to assembly, to not enforce the decision of which target should be the
    // unconditional jump just now.
    block.exit =
        new CodeBlock.ExitArity.Two(relation, getCodeBlock(trueTarget), getCodeBlock(falseTarget));
  }

  private static Block getJumpTarget(Node modeX) {
    return Iterables.getOnlyElement(
        seq(BackEdges.getOuts(modeX)).map(be -> be.node).ofType(Block.class));
  }

  @Override
  public void visit(Cmp node) {
    // There shouldn't be more than one usage in the current block, which would be a Cond.
    // There can't be a Phi in a successor because of critical edges.
    // For the case where the successor in the current block isn't a Cond, there might be multiple
    // Phi b's having this as an argument, but that's uninteresting as we would do the setcc anyway.
    // So: if there is a out edge to a Cond in this block, it will be the only successor in this
    // block. That's crucial, because we may schedule the Cmp and its successor as the last
    // instructions of the block without risking data dependency violations arising from other uses
    // of the Cmp.
    Block irBlock = (Block) node.getBlock();
    boolean isMatchedOnInThisBlock =
        seq(BackEdges.getOuts(node))
                .map(be -> be.node)
                .ofType(Cond.class)
                .filter(cond -> irBlock.equals(cond.getBlock()))
                .count()
            > 0;
    if (isMatchedOnInThisBlock && retainControlFlow) {
      // We retain the computation of the Cmp, reasoning in the above comment.
      retainedComputations.add(node);
      return;
    }

    super.visit(node);
    lastCmp.put(irBlock, node);
  }

  @Override
  public void visit(Proj node) {
    if (node.getMode().equals(Mode.getX())) {
      // We handle these together with the Cond matched on.
      return;
    }
    super.visit(node);
  }

  @Override
  public void visit(Jmp jmp) {
    Block target = getJumpTarget(jmp);
    ExitArity exit = new One(getCodeBlock(target));
    getCodeBlockOfNode(jmp).exit = exit;
  }

  @Override
  public void visit(Return node) {
    CodeBlock block = getCodeBlockOfNode(node);
    if (node.getPredCount() > 1) {
      Node retVal = node.getPred(1);
      // Important invariant: we arrange the calls to the TreeMatcher so that retVal has a
      // VirtualRegister.
      VirtualRegister register = mapping.registerForNode(retVal);
      assert mapping.getDefinition(register) != null : "retVal was not in a register " + retVal;
      OperandWidth width = modeToWidth(retVal.getMode());
      RegisterOperand source = new RegisterOperand(width, register);
      RegisterOperand dest = new RegisterOperand(width, mapping.registerForNode(node));
      block.instructions.add(new Mov(source, dest));
    }
    block.instructions.add(new Leave());
    block.exit = new CodeBlock.ExitArity.Zero();
  }

  @Override
  public void visit(Start node) {
    getCodeBlock(graph.getStartBlock()).instructions.add(0, new Enter());
  }

  @Override
  public void visit(End node) {
    // Do nothing (?)
  }

  @Override
  public void visit(firm.nodes.Anchor node) {
    // We ignore these
  }

  public static BiMap<Block, CodeBlock> selectInstructions(
      Graph graph, ActivationRecord activationRecord) {
    InstructionSelector selector = new InstructionSelector(graph, activationRecord);
    List<Node> topologicalOrder = GraphUtils.topologicalOrder(graph);
    return FirmUtils.withBackEdges(
        graph,
        () -> {
          topologicalOrder.forEach(n -> n.accept(selector));
          selector.retainControlFlow = false;
          selector.retainedComputations.forEach(n -> n.accept(selector));
          return selector.blocks;
        });
  }
}
