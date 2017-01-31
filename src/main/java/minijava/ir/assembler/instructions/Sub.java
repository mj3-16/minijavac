package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

/** Important note: Sub(x, y) === y = y - x */
public class Sub extends BinaryInstruction {
  public Sub(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.SUB;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
