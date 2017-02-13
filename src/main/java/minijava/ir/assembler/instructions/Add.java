package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

public class Add extends TwoAddressInstruction {
  public Add(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
