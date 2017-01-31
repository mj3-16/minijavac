package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

public class Mul extends BinaryInstruction {
  public Mul(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.MUL;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
