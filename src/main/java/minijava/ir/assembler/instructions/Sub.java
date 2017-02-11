package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Sub extends TwoAddressInstruction {
  public Sub(Operand subtrahend, RegisterOperand minuend, Register result) {
    super(subtrahend, minuend, result);
  }

  public Sub(Operand left, RegisterOperand rightIn, RegisterOperand rightOut) {
    super(left, rightIn, rightOut);
  }

  public Sub(Operand left, MemoryOperand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
