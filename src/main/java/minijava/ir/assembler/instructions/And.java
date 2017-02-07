package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class And extends Instruction {
  public And(Operand left, RegisterOperand right, VirtualRegister result) {
    super(newArrayList(left, right), newArrayList(result));
  }

  public And(Operand left, MemoryOperand right) {
    super(newArrayList(left, right), newArrayList());
  }
}
