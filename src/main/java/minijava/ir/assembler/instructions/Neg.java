package minijava.ir.assembler.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;

public class Neg extends CodeBlockInstruction {
  public final Operand inout;

  public Neg(Operand inout) {
    super(newArrayList(inout), newArrayList(inout));
    checkArgument(
        !(inout instanceof ImmediateOperand), "Can't negate an immedate since it's not writable");
    this.inout = inout;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
