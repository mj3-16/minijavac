package minijava.ir.assembler.instructions;

public class Call extends Instruction {

  public final String targetLdName;

  public Call(String targetLdName) {
    this.targetLdName = targetLdName;
  }

  @Override
  public String toGNUAssembler() {
    return "\n" + super.toGNUAssembler() + " " + targetLdName;
  }

  @Override
  public Type getType() {
    return Type.CALL;
  }
}
