package minijava.backend.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.Operand;

public class Push extends Instruction {
  public final Operand input;

  public Push(Operand input) {
    super(newArrayList(input), newArrayList());
    this.input = input;
    setMayBeMemory(input);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
