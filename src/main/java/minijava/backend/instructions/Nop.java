package minijava.backend.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.Operand;

public class Nop extends CodeBlockInstruction {
  public final Operand op;

  public Nop(Operand op) {
    super(newArrayList(), newArrayList(op));
    checkArgument(!(op instanceof ImmediateOperand), "Can't nop with an immediate");
    this.op = op;
    setMayBeMemory(op);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
