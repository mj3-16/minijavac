package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

public class CLTD extends Instruction {
  public CLTD(RegisterOperand op, VirtualRegister resultLow, VirtualRegister resultHigh) {
    super(newArrayList(op), newArrayList(resultLow, resultHigh));
    assert isConstrainedToRegister(op.register, AMD64Register.A);
    assert isConstrainedToRegister(resultLow, AMD64Register.A);
    assert isConstrainedToRegister(resultHigh, AMD64Register.D);
  }
}
