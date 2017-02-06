package minijava.ir.assembler.instructions;

import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.registers.AMD64Register;

public class Call extends Instruction {
  public String label;

  public Call(String label, List<Operand> arguments) {
    super(arguments, new ArrayList<>(AMD64Register.allocatable));
    this.label = label;
  }
}
