package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

public class Add extends BinaryInstruction {
  public Add(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.ADD;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
