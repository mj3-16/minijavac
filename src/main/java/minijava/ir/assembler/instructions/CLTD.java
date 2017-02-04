package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;

public class CLTD extends Instruction {
  public CLTD(RegisterOperand op, Register resultLow, Register resultHigh) {
    super(op, resultLow, resultHigh);
    assert isConstrainedToRegister(op.register, AMD64Register.A);
    assert isConstrainedToRegister(resultLow, AMD64Register.A);
    assert isConstrainedToRegister(resultHigh, AMD64Register.D);
  }
}
