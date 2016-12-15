package minijava.ir.assembler.instructions;

import minijava.ir.assembler.location.Register;

/** A binary instruction with two arguments */
public abstract class BinaryInstruction extends Instruction {
  public final Argument left;
  public final Argument right;

  public BinaryInstruction(Argument left, Argument right) {
    this.left = left;
    this.right = right;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(left, right);
  }

  @Override
  protected Register.Width getWidthOfArguments() {
    return getMaxWithOfArguments(left, right);
  }
}
