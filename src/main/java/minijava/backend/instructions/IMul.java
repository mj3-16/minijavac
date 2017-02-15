package minijava.backend.instructions;

import minijava.backend.operands.Operand;

public class IMul extends TwoAddressInstruction {
  public IMul(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}