package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class Neg extends Instruction {
  public Neg(Operand operand, VirtualRegister result) {
    super(newArrayList(operand), newArrayList(new RegisterOperand(operand.width, result)));
  }
}
