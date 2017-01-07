package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.Register;

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
    super(Register.Width.Quad);
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

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
