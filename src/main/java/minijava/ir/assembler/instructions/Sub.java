package minijava.ir.assembler.instructions;

/** Important note: Sub(x, y) === y = y - x */
public class Sub extends BinaryInstruction {
  public Sub(Argument left, Argument right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.SUB;
  }
}
