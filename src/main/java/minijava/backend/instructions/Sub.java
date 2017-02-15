package minijava.backend.instructions;

import minijava.backend.operands.Operand;

public class Sub extends TwoAddressInstruction {
  public Sub(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
