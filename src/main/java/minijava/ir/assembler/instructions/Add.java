package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class Add extends Instruction {
  public Add(Operand left, RegisterOperand right, VirtualRegister result) {
    super(newArrayList(left, right), newArrayList(result));
    //connect(right.register, output);
  }

  public Add(Operand left, MemoryOperand right) {
    super(newArrayList(left, right), newArrayList());
  }
}
