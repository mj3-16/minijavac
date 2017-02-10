package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Sub extends TwoAddressInstruction {
  public Sub(Operand subtrahend, RegisterOperand minuend, Register result) {
    super(subtrahend, minuend, result);
  }
}
