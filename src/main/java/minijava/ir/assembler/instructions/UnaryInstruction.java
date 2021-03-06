package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** An instruction with one argument */
public abstract class UnaryInstruction extends Instruction {
  public final Argument arg;

  public UnaryInstruction(Argument arg) {
    super(arg.width);
    this.arg = arg;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(arg);
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of(arg);
  }
}
