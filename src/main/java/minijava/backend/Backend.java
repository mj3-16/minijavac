package minijava.backend;

import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.Program;
import firm.nodes.Block;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
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
import minijava.ir.utils.Dominance;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;

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
    List<Block> topologicalOrder =
        seq(GraphUtils.topologicalOrder(graph))
            .ofType(Block.class)
            .filter(b -> !b.equals(graph.getEndBlock()))
            .toList();

    // GraphUtils.topologicalOrder computes a linearization where each predecessor comes before its successors (modulo
    // back edges, Phis and Blocks that is). However this order will not respect dominance: E.g. loop headers always
    // come after loop bodies due to the way we break loops.
    // Just like in the text book topo sort, we can use this topological order for a visitor order in a DFS ignoring
    // back edges (thus respecting dominance!) to get a linearization with the desired properties.

    List<CodeBlock> linearization =
        seq(dfsFinishOrderIgnoringBackEdges(topologicalOrder)).map(blocks::get).toList();

    for (int i = 0; i < linearization.size(); i++) {
      linearization.get(i).linearizedOrdinal = i;
    }

    return linearization;
  }

  private static List<Block> dfsFinishOrderIgnoringBackEdges(List<Block> visitOrder) {
    List<Block> ret = new ArrayList<>();
    Set<Block> visited = new HashSet<>();
    visitOrder.forEach(b -> dfsFinishOrderIgnoringBackEdgesHelper(b, visited, ret));
    return ret;
  }

  private static void dfsFinishOrderIgnoringBackEdgesHelper(
      Block cur, Set<Block> visited, List<Block> ret) {
    if (visited.contains(cur)) {
      return;
    }
    visited.add(cur);

    for (Block pred : NodeUtils.getPredecessorBlocks(cur)) {
      if (Dominance.dominates(cur, pred)) {
        // Back edge!
        continue;
      }
      dfsFinishOrderIgnoringBackEdgesHelper(pred, visited, ret);
    }

    ret.add(cur);
  }
}
