package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.registers.Register;

public class Neg extends Instruction {
  public Neg(Operand operand, Register result) {
    super(operand, result);
  }
}
