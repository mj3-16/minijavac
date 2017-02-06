package minijava.ir.assembler;

import firm.Graph;
import firm.Program;
import firm.nodes.Block;
import java.util.HashMap;
import java.util.Map;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.optimize.ProgramMetrics;

public class Backend {
  public static String lowerAssembler(String outFile) {
    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    Map<Block, CodeBlock> blocks = new HashMap<>();
    for (Graph graph : metrics.reachableFromMain()) {
      ActivationRecord activationRecord = new ActivationRecord();
      blocks.putAll(InstructionSelector.selectInstructions(graph, activationRecord));
    }

    // TODO: allocate registers, output asm

    return null;
  }
}
