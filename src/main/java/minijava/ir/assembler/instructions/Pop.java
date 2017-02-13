package minijava.ir.assembler.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;

public class Pop extends Instruction {
  public final Operand output;

  public Pop(Operand output) {
    super(newArrayList(), newArrayList(output));
    checkArgument(!(output instanceof ImmediateOperand), "Can't pop into an immediate");
    this.output = output;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
