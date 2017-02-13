package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

public class And extends TwoAddressInstruction {
  public And(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
