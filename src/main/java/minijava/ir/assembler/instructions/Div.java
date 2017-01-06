package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * <code>idivl</code> instruction.
 *
 * <p>The upper 32 bit of the dividend have to be in the EDX register and the lower 32 bit in the
 * EAX register.
 *
 * <p>The quotient is then placed into the EAX register and the remainder into the EDX register.
 *
 * <p>Be sure to use the {@link CLTD} instruction before.
 */
public class Div extends Instruction {

  public final Argument divisor;

  public Div(Argument divisor) {
    this.divisor = divisor;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(divisor);
  }

  @Override
  public Type getType() {
    return Type.DIV;
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of();
  }
}
