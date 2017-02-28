package minijava.backend;

import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.Program;
import firm.nodes.Block;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import minijava.backend.allocation.AllocationResult;
import minijava.backend.allocation.LinearScanRegisterAllocator;
import minijava.backend.block.CodeBlock;
import minijava.backend.cleanup.PeepholeOptimizer;
import minijava.backend.deconstruction.SsaDeconstruction;
import minijava.backend.instructions.Instruction;
import minijava.backend.lifetime.LifetimeAnalysis;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.selection.InstructionSelector;
import minijava.backend.syntax.GasSyntax;
import minijava.ir.optimize.ProgramMetrics;
import minijava.ir.utils.DominanceTree;
import minijava.ir.utils.GraphUtils;
import org.jooq.lambda.Seq;

public class Backend {

  public static String lowerAssembler(String outFile) throws IOException {
    StringBuilder asm = new StringBuilder();
    GasSyntax.formatHeader(asm);
    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    for (Graph graph : metrics.reachableFromMain()) {
      Map<Block, CodeBlock> blocks = InstructionSelector.selectInstructions(graph);
      List<CodeBlock> linearization = linearizeCfg(blocks, graph);
      linearization.forEach(CodeBlock::printDebugInfo);
      LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(linearization);

      System.out.println();
      lifetimes.fixedIntervals.values().forEach(System.out::println);
      for (LifetimeInterval interval : lifetimes.virtualIntervals.values()) {
        System.out.println(interval);
      }

      AllocationResult allocationResult =
          LinearScanRegisterAllocator.allocateRegisters(lifetimes); //, newHashSet(DI, SI));
      allocationResult.printDebugInfo();

      List<Instruction> deconstructed =
          SsaDeconstruction.assembleInstructionList(linearization, allocationResult);
      List<Instruction> instructions = PeepholeOptimizer.optimize(graph, deconstructed);
      GasSyntax.formatGraphInstructions(asm, instructions, graph, allocationResult);
    }

    String asmFile = outFile + ".s";
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(asmFile))) {
      System.out.print(asm);
      writer.append(asm);
    }
    return asmFile;
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
