package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;

public class Mod extends Instruction {

  public Mod(RegisterOperand left, Operand right, Register resultLow, Register resultHigh) {
    super(left, right, resultLow, resultHigh);
    assert isConstrainedToRegister(left.register, AMD64Register.A);
    assert isConstrainedToRegister(resultLow, AMD64Register.A);
    assert isConstrainedToRegister(resultLow, AMD64Register.D);
  }
}
