package minijava.ir.assembler.block;

import java.util.ArrayList;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.instructions.Instruction;

/** A list of assembler instructions with a label */
public class CodeBlock extends ArrayList<Instruction> implements GNUAssemblerConvertible {

  private final String label;

  public CodeBlock(String label) {
    this.label = label;
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    builder.append("\n");
    builder.append(label).append(":");
    for (Instruction instruction : this) {
      builder.append("\n\t");
      builder.append(instruction.toGNUAssembler());
    }
    return builder.toString();
  }
}
