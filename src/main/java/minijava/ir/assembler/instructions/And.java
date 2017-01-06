package minijava.ir.assembler.instructions;

/** Bitwise and */
public class And extends BinaryInstruction {

  public And(Argument left, Argument right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.AND;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
