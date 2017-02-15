package minijava.backend.instructions;

import minijava.backend.operands.Operand;

public class Add extends TwoAddressInstruction {
  public Add(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
