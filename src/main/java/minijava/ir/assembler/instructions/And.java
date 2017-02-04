package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class And extends Instruction {
  public And(Operand left, RegisterOperand right, Register result) {
    super(left, right, result);
  }

  public And(Operand left, MemoryOperand right, Register result) {
    super(left, right, result);
  }
}
