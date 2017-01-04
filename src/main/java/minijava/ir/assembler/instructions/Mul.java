package minijava.ir.assembler.instructions;

public class Mul extends BinaryInstruction {
  public Mul(Argument left, Argument right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.MUL;
  }
}
