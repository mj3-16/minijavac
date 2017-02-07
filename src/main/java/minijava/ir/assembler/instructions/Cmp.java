package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;

public class Cmp extends Instruction {
  // As with Test, this modifies the FLAGS register. Not sure if we should also model it through
  // a register constraint.
  public Cmp(Operand left, Operand right) {
    super(newArrayList(left, right), newArrayList());
  }
}
