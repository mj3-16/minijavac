package minijava.backend.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;

/** A binary instruction, where the right of the two input operands is also an output operand. */
public abstract class TwoAddressInstruction extends CodeBlockInstruction {
  public final Operand left;
  public final Operand right;

  protected TwoAddressInstruction(Operand left, Operand right) {
    // This is only so that we can reference rightOut.
    super(newArrayList(left, right), newArrayList(right));
    checkArgument(
        !(left instanceof MemoryOperand && right instanceof MemoryOperand),
        "Both operands where MemoryOperands");
    this.left = left;
    this.right = right;
    // Either left or right may be memory operands, not both.
    // We mark left as the possible one, iff right is't already a MemoryOperand.
    if (!(right instanceof MemoryOperand)) {
      setMayBeMemory(left);
    }
  }
}
