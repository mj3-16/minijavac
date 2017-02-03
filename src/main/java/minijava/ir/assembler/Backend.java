package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.Block;
import java.util.HashMap;
import java.util.Map;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.optimize.ProgramMetrics;

public class Backend {
  public static String lowerAssembler(String outFile) {
    ProgramMetrics metrics = new ProgramMetrics();
    Map<Block, CodeBlock> blocks = new HashMap<>();
    for (Graph graph : metrics.reachableFromMain()) {
      blocks.putAll(InstructionSelector.selectInstructions(graph));
    }

    // TODO: allocate registers, output asm

    return null;
  }
}
