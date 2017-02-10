package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

/**
 * A binary instruction, where the right of the two input operands is also an output operand. The
 * destination is modeled by an additional Register dest,
 */
public abstract class TwoAddressInstruction extends Instruction {
  protected TwoAddressInstruction(Operand left, RegisterOperand right, Register result) {
    this(left, right, new RegisterOperand(right.width, result));
  }

  private TwoAddressInstruction(Operand left, RegisterOperand rightIn, RegisterOperand rightOut) {
    // This is only so that we can reference rightOut.
    super(newArrayList(left, rightIn), newArrayList(rightOut));
    setHint(rightIn, rightOut);
  }
}
