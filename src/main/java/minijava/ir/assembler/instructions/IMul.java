package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class IMul extends Instruction {
  public IMul(Operand left, RegisterOperand right, VirtualRegister result) {
    super(newArrayList(left, right), newArrayList(result));
  }
}
