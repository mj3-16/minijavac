package minijava.ir.assembler.instructions;

/**
 * Convert Signed Long to Signed Double Long
 *
 * <p>Sign-extends EAX, resulting in EDX:EAX
 */
public class CLTD extends Instruction {

  @Override
  public Type getType() {
    return Type.CLTD;
  }
}
