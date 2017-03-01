package minijava.backend.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;

public class Test extends CodeBlockInstruction {
  public final Operand left;
  public final Operand right;
  // We could also handle the FLAGS register as constraints, I guess... But this gets awkward
  // with spilling and getting operands and stuff.

  public Test(Operand left, Operand right) {
    super(newArrayList(left, right), newArrayList());
    checkArgument(left.width == right.width, "Test operands should have same width");
    checkArgument(!(left instanceof ImmediateOperand), "Test's left operand can't be an immediate");
    this.left = left;
    this.right = right;
    if (!(right instanceof MemoryOperand) && !left.equals(right)) {
      setMayBeMemory(right);
    }
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
