package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Test extends Instruction {
  // We could also handle the FLAGS register as constraints, I guess... But this gets awkward
  // with spilling and getting operands and stuff.

  public Test(RegisterOperand left, Operand right) {
    super(left, right);
  }

  public Test(ImmediateOperand left, Operand right) {
    super(left, right);
  }
}
