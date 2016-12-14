package minijava.ir.assembler.instructions;

/** Pushes a value on the stack */
public class Push extends UnaryInstruction {

  public Push(Argument arg) {
    super(arg);
  }

  @Override
  public Type getType() {
    return Type.PUSH;
  }
}
