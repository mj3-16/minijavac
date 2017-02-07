package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jooq.lambda.Seq;

public class CLTD extends Instruction {
  public CLTD(RegisterOperand op, VirtualRegister resultLow, VirtualRegister resultHigh) {
    super(newArrayList(op), toOperands(op.width, Seq.of(resultLow, resultHigh)));
    assert isConstrainedToRegister(op.register, AMD64Register.A);
    assert isConstrainedToRegister(resultLow, AMD64Register.A);
    assert isConstrainedToRegister(resultHigh, AMD64Register.D);
  }
}
