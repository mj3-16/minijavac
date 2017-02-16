package minijava.backend.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;

public class Xchg extends Instruction {
  public final Operand left;
  public final Operand right;

  public Xchg(Operand left, Operand right) {
    super(newArrayList(left, right), newArrayList(left, right));
    this.left = left;
    this.right = right;
    checkArgument(
        !(left instanceof ImmediateOperand || right instanceof ImmediateOperand),
        "Can't exchange immediates");
    checkArgument(
        left instanceof RegisterOperand || right instanceof RegisterOperand,
        "One operand must be a register");
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
