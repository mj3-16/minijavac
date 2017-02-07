package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Sub extends Instruction {
  public Sub(Operand subtrahend, RegisterOperand minuend, Register result) {
    super(
        newArrayList(subtrahend, minuend),
        newArrayList(new RegisterOperand(minuend.width, result)));
  }
}
