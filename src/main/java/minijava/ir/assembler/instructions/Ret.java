package minijava.ir.assembler.instructions;

/** <code>ret</code> instruction that returns from a function call */
public class Ret extends Instruction {
  @Override
  public Type getType() {
    return Type.RET;
  }
}
