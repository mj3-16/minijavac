package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class Mov extends Instruction {
  public Mov(Operand src, MemoryOperand dest) {
    super(newArrayList(src, dest), newArrayList());
  }

  public Mov(Operand src, RegisterOperand dest) {
    super(newArrayList(src, dest), newArrayList());
    if (dest.register instanceof VirtualRegister) {
      outputs.add((VirtualRegister) dest.register);
    }
    assert src.width == dest.width;
  }
}
