package minijava.ir.assembler;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.BiMap;
import firm.Graph;
import firm.Program;
import firm.nodes.Block;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.assembler.allocation.AllocationResult;
import minijava.ir.assembler.allocation.LinearScanRegisterAllocator;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.deconstruction.SsaDeconstruction;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.lifetime.LifetimeAnalysis;
import minijava.ir.assembler.lifetime.LifetimeAnalysisResult;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.optimize.ProgramMetrics;
import minijava.ir.utils.GraphUtils;
import org.jooq.lambda.Seq;

public class Backend {

  public static String lowerAssembler(String outFile) {
    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    Map<Block, CodeBlock> blocks = new HashMap<>();
    for (Graph graph : metrics.reachableFromMain()) {
      BiMap<Block, CodeBlock> currentFunction = InstructionSelector.selectInstructions(graph);
      blocks.putAll(currentFunction);
      List<CodeBlock> linearization = linearizeCfg(blocks, graph);
      for (CodeBlock block : linearization) {
        System.out.println(block.label + ":");
        block.phis.forEach(System.out::println);
        block.instructions.forEach(System.out::println);
        System.out.println(block.exit);
      }
      LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(currentFunction, linearization);

      System.out.println();
      System.out.println("Lifetimes:");
      lifetimes.fixedIntervals.values().forEach(System.out::println);
      for (LifetimeInterval interval : lifetimes.virtualIntervals) {
        System.out.println(interval);
      }

      AllocationResult allocationResult = LinearScanRegisterAllocator.allocateRegisters(lifetimes);
      System.out.println();
      System.out.println("Allocation results:");
      for (LifetimeInterval interval :
          seq(allocationResult.allocation.keySet()).sorted(li -> li.register.id)) {
        System.out.println(interval.register + " -> " + allocationResult.allocation.get(interval));
      }

      List<Instruction> instructions = SsaDeconstruction.assembleInstructionList(allocationResult);
    }

    return null;
  }

  private static List<CodeBlock> linearizeCfg(Map<Block, CodeBlock> blocks, Graph graph) {
    Seq<Block> allBlocks =
        seq(GraphUtils.topologicalOrder(graph))
            .ofType(Block.class)
            .filter(b -> !b.equals(graph.getEndBlock()));

    DominanceTree tree = DominanceTree.ofBlocks(allBlocks);
    List<CodeBlock> linearization = seq(tree.preorder()).map(blocks::get).toList();

    for (int i = 0; i < linearization.size(); i++) {
      linearization.get(i).linearizedOrdinal = i;
    }

    return linearization;
  }
}
