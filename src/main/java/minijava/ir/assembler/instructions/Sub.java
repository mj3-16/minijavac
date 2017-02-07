package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class Sub extends Instruction {
  public Sub(Operand subtrahend, RegisterOperand minuend, VirtualRegister result) {
    super(newArrayList(subtrahend, minuend), newArrayList(result));
  }

  public Sub(Operand subtrahend, MemoryOperand minuend) {
    super(newArrayList(subtrahend, minuend), newArrayList());
  }
}
