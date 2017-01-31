package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** A binary instruction with two arguments */
public abstract class BinaryInstruction extends Instruction {
  public final Operand left;
  public final Operand right;

  public BinaryInstruction(Operand left, Operand right) {
    super(getWidthOfArguments(BinaryInstruction.class, left, right));
    this.left = left;
    this.right = right;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(left, right);
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of(left, right);
  }
}
