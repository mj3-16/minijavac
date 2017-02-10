package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Add extends TwoAddressInstruction {
  public Add(Operand left, RegisterOperand right, Register result) {
    super(left, right, result);
  }
}
