package minijava.ir.assembler.instructions;

/** Negates the passed argument and stores it back */
public class Neg extends UnaryInstruction {

  public Neg(Argument arg) {
    super(arg);
  }

  @Override
  public Type getType() {
    return Type.NEG;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
