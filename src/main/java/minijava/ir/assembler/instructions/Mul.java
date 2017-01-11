package minijava.ir.assembler.instructions;

public class Mul extends BinaryInstruction {
  public Mul(Argument left, Argument right) {
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
