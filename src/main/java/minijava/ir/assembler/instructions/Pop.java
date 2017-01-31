package minijava.ir.assembler.instructions;

/** Pops a value of the stack */
public class Pop extends UnaryInstruction {

  public Pop(Operand arg) {
    super(arg);
  }

  @Override
  public Type getType() {
    return Type.POP;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
