package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public class Add extends Instruction {
  public Add(Operand left, RegisterOperand right, Register result) {
    super(newArrayList(left, right), newArrayList(new RegisterOperand(right.width, result)));
    //connect(right.register, output);
  }
}
