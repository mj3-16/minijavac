package minijava.ir.assembler.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;

/** A binary instruction, where the right of the two input operands is also an output operand. */
public abstract class TwoAddressInstruction extends CodeBlockInstruction {
  public final Operand left;
  public final Operand right;

  protected TwoAddressInstruction(Operand left, Operand right) {
    // This is only so that we can reference rightOut.
    super(newArrayList(left, right), newArrayList(right));
    checkArgument(!(right instanceof ImmediateOperand), "Right Operand has to be writable");
    this.left = left;
    this.right = right;
  }
}
