package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

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

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of();
  }
}
