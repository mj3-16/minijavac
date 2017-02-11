package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Add extends TwoAddressInstruction {
  public Add(Operand left, RegisterOperand right, Register result) {
    super(left, right, result);
  }

  public Add(Operand left, RegisterOperand rightIn, RegisterOperand rightOut) {
    super(left, rightIn, rightOut);
  }

  public Add(Operand left, MemoryOperand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
