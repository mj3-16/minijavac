package minijava.ir.assembler.instructions;

public class Cmp extends BinaryInstruction {
  public Cmp(Argument left, Argument right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.CMP;
  }
}
