package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

public class Mov extends Instruction {
  public Mov(Operand src, Operand dest) {
    super(src, dest);
    assert src.width == dest.width;
  }
}
