package minijava.ir.assembler;

import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.Program;
import firm.nodes.Block;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.lifetime.LifetimeAnalysis;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.optimize.ProgramMetrics;
import minijava.ir.utils.GraphUtils;
import org.jooq.lambda.Seq;

public class Backend {
  public static String lowerAssembler(String outFile) {
    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    Map<Block, CodeBlock> blocks = new HashMap<>();
    for (Graph graph : metrics.reachableFromMain()) {
      ActivationRecord activationRecord = new ActivationRecord();
      Map<Block, CodeBlock> currentFunction =
          InstructionSelector.selectInstructions(graph, activationRecord);
      blocks.putAll(currentFunction);
      Seq<Block> allBlocks =
          seq(GraphUtils.topologicalOrder(graph))
              .ofType(Block.class)
              .filter(b -> !b.equals(graph.getEndBlock()));
      DominanceTree tree = DominanceTree.ofBlocks(allBlocks);
      List<Block> linearization = tree.preorder();
      for (Block irBlock : linearization) {
        CodeBlock block = blocks.get(irBlock);
        System.out.println(block.label + ":");
        block.phis.forEach(System.out::println);
        block.instructions.forEach(System.out::println);
        System.out.println(block.exit);
      }
      Map<VirtualRegister, LifetimeInterval> lifetimes =
          LifetimeAnalysis.analyse(currentFunction, linearization);

      lifetimes.values().forEach(System.out::println);
    }

    // TODO: allocate registers, output asm

    return null;
  }
}
