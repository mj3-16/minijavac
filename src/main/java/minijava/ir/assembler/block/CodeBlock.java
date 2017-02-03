package minijava.ir.assembler.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.assembler.instructions.Instruction;

public class CodeBlock {
  public final String label;
  public final Set<PhiFunction> phis = new HashSet<>();
  public final List<Instruction> instructions = new ArrayList<>();

  public CodeBlock(String label) {
    this.label = label;
  }
}
