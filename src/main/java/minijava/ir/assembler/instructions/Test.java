package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Test extends Instruction {
  // We could also handle the FLAGS register as constraints, I guess... But this gets awkward
  // with spilling and getting operands and stuff.

  public Test(RegisterOperand left, Operand right) {
    super(newArrayList(left, right), newArrayList());
  }

  public Test(ImmediateOperand left, Operand right) {
    super(newArrayList(left, right), newArrayList());
  }
}
