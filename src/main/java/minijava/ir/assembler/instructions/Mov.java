package minijava.ir.assembler.instructions;

public class Mov extends BinaryInstruction {

  public Mov(Argument left, Argument right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.MOV;
  }
}
