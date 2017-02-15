package minijava.backend;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.BiMap;
import firm.Graph;
import firm.Program;
import firm.nodes.Block;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import minijava.backend.registers.VirtualRegister;
import minijava.backend.selection.InstructionSelector;
import minijava.backend.syntax.GasSyntax;
import minijava.ir.optimize.ProgramMetrics;
import minijava.ir.utils.DominanceTree;
import minijava.ir.utils.GraphUtils;
import org.jooq.lambda.Seq;

public class Backend {

  public static String lowerAssembler(String outFile) throws IOException {
    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    List<Instruction> instructions = new ArrayList<>();
    Map<Block, CodeBlock> blocks = new HashMap<>();
    for (Graph graph : metrics.reachableFromMain()) {
      BiMap<Block, CodeBlock> currentFunction = InstructionSelector.selectInstructions(graph);
      blocks.putAll(currentFunction);
      List<CodeBlock> linearization = linearizeCfg(blocks, graph);
      for (CodeBlock block : linearization) {
        System.out.println(block.label + ":");
        block.phis.forEach(phi -> System.out.println("  " + phi));
        block.instructions.forEach(i -> System.out.println("  " + i));
        System.out.println("  " + block.exit);
      }
      LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(currentFunction, linearization);

      System.out.println();
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
        for (LifetimeInterval splitPart : allocationResult.splitLifetimes.get(interval.register)) {
          System.out.println("  " + splitPart.ranges.from());
        }
      }
      System.out.println();
      System.out.println("Spill slots:");
      for (Map.Entry<VirtualRegister, Integer> entry :
          seq(allocationResult.spillSlots.entrySet()).sorted(s -> s.getKey().id)) {
        System.out.println(entry.getKey() + " -> " + entry.getValue());
      }

      instructions.addAll(
          SsaDeconstruction.assembleInstructionList(linearization, allocationResult));
    }

    instructions = PeepholeOptimizer.optimize(instructions);

    String asmFile = outFile + ".s";
    StringBuilder asm = GasSyntax.formatAssembler(instructions);
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(asmFile))) {
      System.out.println(asm);
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
