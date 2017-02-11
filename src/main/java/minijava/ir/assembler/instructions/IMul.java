package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class IMul extends TwoAddressInstruction {
  public IMul(Operand left, RegisterOperand right, Register result) {
    super(left, right, result);
  }

  public IMul(Operand left, RegisterOperand rightIn, RegisterOperand rightOut) {
    super(left, rightIn, rightOut);
  }

  public IMul(Operand left, MemoryOperand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
