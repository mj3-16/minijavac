package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;

public class Push extends Instruction {
  public final Operand input;

  public Push(Operand input) {
    super(newArrayList(input), newArrayList());
    this.input = input;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
