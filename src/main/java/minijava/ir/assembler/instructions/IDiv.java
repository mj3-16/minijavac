package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;

public class IDiv extends Instruction {

  public IDiv(RegisterOperand dividend, Operand divisor, Register quotient, Register remainder) {
    super(dividend, divisor, quotient, remainder);
    assert isConstrainedToRegister(dividend.register, AMD64Register.A);
    assert isConstrainedToRegister(quotient, AMD64Register.A);
    assert isConstrainedToRegister(remainder, AMD64Register.D);
  }
}
