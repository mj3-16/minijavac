package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** A binary instruction with two arguments */
public abstract class BinaryInstruction extends Instruction {
  public final Argument left;
  public final Argument right;

  public BinaryInstruction(Argument left, Argument right) {
    super(getWidthOfArguments(BinaryInstruction.class, left, right));
    this.left = left;
    this.right = right;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(left, right);
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of(left, right);
  }
}
