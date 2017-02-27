package minijava.backend.selection;

import static firm.bindings.binding_irnode.ir_opcode.iro_Block;
import static firm.bindings.binding_irnode.ir_opcode.iro_Cmp;
import static firm.bindings.binding_irnode.ir_opcode.iro_Cond;
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
import firm.bindings.binding_irnode;
import firm.nodes.Block;
import firm.nodes.Call;
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
import firm.nodes.Store;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.backend.SystemVAbi;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.CodeBlock.ExitArity.One;
import minijava.backend.block.PhiFunction;
import minijava.backend.instructions.CodeBlockInstruction;
import minijava.backend.instructions.Enter;
import minijava.backend.instructions.Instruction;
import minijava.backend.instructions.Leave;
import minijava.backend.instructions.Mov;
import minijava.backend.instructions.Setcc;
import minijava.backend.instructions.Test;
import minijava.backend.operands.Operand;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.VirtualRegister;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.MethodInformation;
import minijava.ir.utils.NodeUtils;
import minijava.ir.utils.ProjPair;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class InstructionSelector extends NodeVisitor.Default {

  private final Graph graph;
  private final VirtualRegisterMapping mapping = new VirtualRegisterMapping();
  private final BiMap<Block, CodeBlock> blocks = HashBiMap.create();
  private final Map<Block, Node> currentlyVisibleModeb = new HashMap<>();
  private final TreeMatcher matcher = new TreeMatcher(mapping);

  private InstructionSelector(Graph graph) {
    this.graph = graph;
  }

  @Override
  public void defaultVisit(Node node) {
    if (node.getMode().equals(Mode.getM())) {
      // Memory edges did their job at keeping side-effects in order and kann just be erased now.
      return;
    }

    // Determine if we really have to generate an intermediate value in a register for this.

    if (!usedMultipleTimes(node) && !usedInSuccessorBlock(node) && !neededInRegister(node)) {
      // We don't handle these cases here, as the node matcher does not need to put intermediate
      // results in registers.
      return;
    }

    // Otherwise we are 'unlucky' and have to produce code for the subtree at node.
    invokeTreeMatcher(node);
  }

  private void invokeTreeMatcher(Node node) {
    List<CodeBlockInstruction> newInstructions = matcher.match(node);
    CodeBlock block = getCodeBlockOfNode(node);
    block.instructions.addAll(newInstructions);
  }

  private CodeBlock getCodeBlockOfNode(Node node) {
    return getCodeBlock((Block) node.getBlock());
  }

  private CodeBlock getCodeBlock(Block block) {
    CodeBlock codeBlock = blocks.get(block);
    if (codeBlock == null) {
      codeBlock = new CodeBlock(getLabelForBlock(block));
      blocks.put(block, codeBlock);
      // We also have to add all blocks of the loop body, if it's a header.
      if (NodeUtils.hasIncomingBackEdge(block)) {
        for (Block irBodyBlock : GraphUtils.blocksOfLoop(block)) {
          CodeBlock bodyBlock = getCodeBlock(irBodyBlock);
          codeBlock.associatedLoopBody.add(bodyBlock);
        }
      }
    }
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
        .filter(be -> needsItsArgumentInRegister(be.node))
        .isNotEmpty();
  }

  private static boolean needsItsArgumentInRegister(Node node) {
    switch (node.getOpCode()) {
      case iro_Return:
      case iro_Cond:
        return true;
      default:
        return false;
    }
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
    OperandWidth width = modeToWidth(phi.getMode());
    RegisterOperand result = new RegisterOperand(phi, mapping.registerForNode(phi));
    Map<CodeBlock, Operand> args = new HashMap<>();
    for (int i = 0; i < phi.getPredCount(); ++i) {
      Node pred = phi.getPred(i);
      Block predBlock = (Block) phi.getBlock().getPred(i).getBlock();
      VirtualRegister register = mapping.registerForNode(pred);
      args.put(getCodeBlock(predBlock), new RegisterOperand(pred, register));
    }
    CodeBlock block = getCodeBlockOfNode(phi);
    block.phis.add(new PhiFunction(args, result, phi));
  }

  @Override
  public void visit(Cond cond) {
    // Conditional control flow has to be the last group of instructions of a block, so we handle it
    // in a second pass after all other computations (grep for 'retain').
    Block irBlock = (Block) cond.getBlock();
    CodeBlock block = getCodeBlock(irBlock);
    // We rely on the topological ordering also present in onlyRetained and assume
    // that the Cmp we match on is still visible through the flags register, if the Cond is
    // in the same block as the Cmp. For nodes other than Cmp, we have to rematerialize the flags register
    // with a Test instruction.
    Node sel = cond.getSelector();
    Relation relation; // Needed for the conditional jump.
    boolean isCmp = sel.getOpCode() == iro_Cmp;
    boolean selInSameBlock = irBlock.equals(sel.getBlock());
    boolean flagsStillVisible = sel.equals(currentlyVisibleModeb.get(irBlock));
    // The following holds because we retained the Cmp we select on, because it has a Cond usage in this block.
    // This means that the flags are still visible.
    // Conversely, if the flags of the sel are still visible, it means that sel.getBlock() == irBlock.
    assert !isCmp || selInSameBlock == flagsStillVisible
        : "isCmp => (selInSameBlock <=> flagsStillVisible)";
    // The following holds because only Cmp and Test instructions set the flag register, but Test instructions
    // are never generated by the TreeMatcher. Only the InstructionSelection inserts them if we need to materialize
    // the flags register.
    assert !flagsStillVisible || isCmp : "flagsStillVisible => isCmp";

    if (flagsStillVisible) {
      assert isCmp : "This is a consequence of the assertion above";
      Cmp cmp = (Cmp) sel;
      relation = cmp.getRelation();
    } else {
      // This handles two cases identically:
      // 1. sel is a Cmp, but not in this block. We have to rematerialize the flags register.
      // 2. sel is not a Cmp. In this case we can't see the value in the flags register and also have to rematerialize.

      // We have to rematerialize the flags register with the node's value.
      assert mapping.hasRegisterAssigned(sel) : "Didn't find the definition for the selector node";
      VirtualRegister selResult = mapping.registerForNode(sel);
      RegisterOperand op = new RegisterOperand(sel, selResult);
      block.instructions.add(new Test(op, op));
      currentlyVisibleModeb.put(irBlock, sel);
      relation = Relation.LessGreater; // Should output in a jnz/jne
    }

    // The flags register is set and we know the relation we want to branch with. Now we find our condition jmp Projs
    // and set the appropriate CodeBlock exit.

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
    // Conditional control flow has to be the last group of instructions of a block, so we handle it
    // in a second pass after all other computations (grep for 'retain').
    // This is not the case if the Cmp node is only used in another block than node.getBlock(), in which case we
    // generate the 'spill' to a general purpose register directly.
    invokeTreeMatcher(node);
    // This will only insert instructions set the flags register (e.g. Cmp, Test). If there are later uses,
    // we also have to do the 'spill'.
    Block irBlock = (Block) node.getBlock();
    if (!usedMultipleTimes(node) && isMatchedOnInSameBlock(node)) {
      // We can omit the Setcc virtual register, which is the defining instruction
      VirtualRegister register = mapping.registerForNode(node);
      Instruction setcc = mapping.getDefinition(register);
      assert setcc instanceof Setcc; // Just to be sure
      getCodeBlock(irBlock).instructions.remove(setcc);
    }
    currentlyVisibleModeb.put(irBlock, node);
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
    getCodeBlockOfNode(jmp).exit = new One(getCodeBlock(target));
  }

  @Override
  public void visit(Return node) {
    CodeBlock block = getCodeBlockOfNode(node);
    if (node.getPredCount() > 1) {
      Node retVal = node.getPred(1);
      // Important invariant: we arrange the calls to the TreeMatcher so that retVal has a
      // VirtualRegister.
      assert mapping.hasRegisterAssigned(retVal) : "retVal was not in a register " + retVal;
      VirtualRegister register = mapping.registerForNode(retVal);
      OperandWidth width = modeToWidth(retVal.getMode());
      RegisterOperand source = new RegisterOperand(retVal, register);
      RegisterOperand dest = new RegisterOperand(retVal, SystemVAbi.RETURN_REGISTER);
      block.instructions.add(new Mov(source, dest));
    }
    block.instructions.add(new Leave());
    block.exit = new CodeBlock.ExitArity.Zero();
  }

  @Override
  public void visit(Call node) {
    invokeTreeMatcher(node);
  }

  @Override
  public void visit(Store node) {
    // These are only forced by their side-effects, which we completely ignore everywhere else.
    invokeTreeMatcher(node);
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

  /**
   * Because of the evaluation side-effects of mode b nodes on the flags register, we retain code
   * gen for Cond and Cmp nodes. After all other instructions are selected, we traverse Conds in
   * reverse topological order to be sure no intermediate expression tampered with the flags
   * register (e.g. because some later data dependency needed to evaluate a mode b node in a prior
   * block).
   */
  private static boolean isToBeRetained(Node node) {
    binding_irnode.ir_opcode opCode = node.getOpCode();
    if (opCode == iro_Cond) {
      return true;
    }

    if (opCode != iro_Cmp) {
      return false;
    }

    Cmp cmp = (Cmp) node;
    return isMatchedOnInSameBlock(cmp);
  }

  private static boolean isMatchedOnInSameBlock(Cmp cmp) {
    // There shouldn't be more than one usage in the current block, which would be a Cond.
    // There can't be a Phi in a successor because of critical edges.
    // For the case where the successor in the current block isn't a Cond, there might be multiple
    // Phi b's having this as an argument, but that's uninteresting as we would do the setcc anyway.
    // So: if there is a out edge to a Cond in this block, it will be the only successor in this
    // block. That's crucial, because we may schedule the Cmp and its successor as the last
    // instructions of the block without risking data dependency violations arising from other uses
    // of the Cmp.
    return seq(BackEdges.getOuts(cmp))
            .map(be -> be.node)
            .ofType(Cond.class)
            .filter(cond -> cmp.getBlock().equals(cond.getBlock()))
            .count()
        > 0;
  }

  public static BiMap<Block, CodeBlock> selectInstructions(Graph graph) {
    return FirmUtils.withBackEdges(
        graph,
        () -> {
          InstructionSelector selector = new InstructionSelector(graph);
          List<Node> topologicalOrder = GraphUtils.topologicalOrder(graph);

          Tuple2<Seq<Node>, Seq<Node>> partition =
              seq(topologicalOrder).partition(InstructionSelector::isToBeRetained);
          // This will not visit Cond(Cmp) when in the same block.
          List<Node> withoutRetained = partition.v2.toList();
          // This will only visit nodes which were retained previously. See isToBeRetained.
          List<Node> onlyRetained = partition.v1.toList();

          withoutRetained.forEach(n -> n.accept(selector));
          // This split is necessary because of the side-effects of instruction ordering on the flags register.
          onlyRetained.forEach(n -> n.accept(selector));
          return selector.blocks;
        });
  }
}
