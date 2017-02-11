package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

/**
 * A binary instruction, where the right of the two input operands is also an output operand. The
 * destination is modeled by an additional Register dest,
 */
public abstract class TwoAddressInstruction extends CodeBlockInstruction {
  public final Operand left;
  public final Operand rightIn;
  public final Operand rightOut;

  protected TwoAddressInstruction(Operand left, RegisterOperand right, Register result) {
    this(left, right, new RegisterOperand(right.width, result));
  }

  protected TwoAddressInstruction(Operand left, MemoryOperand right) {
    this(left, right, right);
  }

  protected TwoAddressInstruction(Operand left, Operand rightIn, Operand rightOut) {
    // This is only so that we can reference rightOut.
    super(newArrayList(left, rightIn), newArrayList(rightOut));
    this.left = left;
    this.rightIn = rightIn;
    this.rightOut = rightOut;
    setHints(rightIn, rightOut);
  }
}
