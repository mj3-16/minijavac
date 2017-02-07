package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jooq.lambda.Seq;

public class IDiv extends Instruction {

  public IDiv(
      RegisterOperand dividend,
      Operand divisor,
      VirtualRegister quotient,
      VirtualRegister remainder) {
    super(newArrayList(dividend, divisor), toOperands(dividend.width, Seq.of(quotient, remainder)));
    assert isConstrainedToRegister(dividend.register, AMD64Register.A);
    assert isConstrainedToRegister(quotient, AMD64Register.A);
    assert isConstrainedToRegister(remainder, AMD64Register.D);
  }
}
