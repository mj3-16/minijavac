package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.Register;

/** An instruction with one argument */
public abstract class UnaryInstruction extends Instruction {
  public final Argument arg;

  public UnaryInstruction(Argument arg) {
    this.arg = arg;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(arg);
  }

  @Override
  protected Register.Width getWidthOfArguments() {
    return getMaxWithOfArguments(arg);
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of(arg);
  }
}
