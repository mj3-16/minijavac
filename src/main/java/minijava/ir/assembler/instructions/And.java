package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class And extends TwoAddressInstruction {
  public And(Operand left, RegisterOperand right, Register result) {
    super(left, right, result);
  }

  public And(Operand left, RegisterOperand rightIn, RegisterOperand rightOut) {
    super(left, rightIn, rightOut);
  }

  public And(Operand left, MemoryOperand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
