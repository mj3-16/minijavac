package minijava.ir.assembler.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;

public class Mov extends CodeBlockInstruction {
  public final Operand src;
  public final Operand dest;

  public Mov(Operand src, Operand dest) {
    super(newArrayList(src, dest), newArrayList(dest));
    checkArgument(src.width == dest.width, "Mov can't widen a value");
    checkArgument(!(dest instanceof ImmediateOperand), "Can't move into an immediate");
    this.src = src;
    this.dest = dest;
    setHints(src, dest);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
