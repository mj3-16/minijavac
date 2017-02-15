package minijava.backend.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;

public class Xchg extends Instruction {
  public final Operand left;
  public final Operand right;

  public Xchg(Operand left, Operand right) {
    super(newArrayList(left, right), newArrayList(left, right));
    this.left = left;
    this.right = right;
    assert left instanceof RegisterOperand || right instanceof RegisterOperand;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
