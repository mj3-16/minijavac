package minijava.ir.assembler;

import static firm.bindings.binding_irnode.ir_opcode.iro_Block;
import static minijava.ir.utils.FirmUtils.modeToWidth;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import com.sun.jna.Platform;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.End;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Phi;
import firm.nodes.Start;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.MethodInformation;

public class InstructionSelector extends NodeVisitor.Default {

  private final Graph graph;
  private final VirtualRegisterMapping mapping = new VirtualRegisterMapping();
  private final Map<Block, CodeBlock> blocks = new HashMap<>();
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

    if (!usedMultipleTimes(node) || !usedInSuccessorBlock(node)) {
      // We don't handle these cases here, as the node matcher does not need to put intermediate
      // results in registers.
      return;
    }

    // Otherwise we are 'unlucky' and have to produce code for the subtree at node.
    List<Instruction> newInstructions = matcher.match(node);
    CodeBlock block = getCodeBlock((Block) node.getBlock());
    block.instructions.addAll(newInstructions);
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
    List<VirtualRegister> args = seq(phi.getPreds()).map(mapping::registerForNode).toList();
    OperandWidth width = modeToWidth(phi.getMode());
    CodeBlock block = getCodeBlock((Block) phi.getBlock());
    block.phis.add(new PhiFunction(args, result, width, phi));
  }

  @Override
  public void visit(End node) {
    // Do nothing (?)
  }

  @Override
  public void visit(Start node) {
    // TODO: prologue. When? After register alloc I presume, when we know the stack size.
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

  private boolean usedInSuccessorBlock(Node node) {
    BackEdges.Edge usage = Iterables.getOnlyElement(BackEdges.getOuts(node));
    assert node.getOpCode() != iro_Block;
    return !node.getBlock().equals(usage.node.getBlock());
  }

  private boolean usedMultipleTimes(Node node) {
    return BackEdges.getNOuts(node) > 1;
  }

  public static Map<Block, CodeBlock> selectInstructions(Graph graph) {
    InstructionSelector selector = new InstructionSelector(graph);
    List<Node> topologicalOrder = GraphUtils.topologicalOrder(graph);
    FirmUtils.withBackEdges(graph, () -> topologicalOrder.forEach(n -> n.accept(selector)));
    return selector.blocks;
  }
}
