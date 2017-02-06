package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Sub extends Instruction {
  public Sub(Operand left, RegisterOperand right, Register result) {
    super(left, right, result);
  }

  public Sub(Operand left, MemoryOperand right) {
    super(left, right);
  }
}
