package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;

/** An instruction with one argument */
public abstract class UnaryInstruction extends Instruction {
  public final Operand arg;

  public UnaryInstruction(Operand arg) {
    super(arg.width);
    this.arg = arg;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(arg);
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of(arg);
  }
}
