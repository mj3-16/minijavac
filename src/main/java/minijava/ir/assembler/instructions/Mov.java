package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Mov extends Instruction {
  public Mov(Operand src, Operand dest) {
    super(src, dest);
    if (dest instanceof RegisterOperand) {
      defined.add(((RegisterOperand) dest).register);
    }
    assert src.width == dest.width;
  }
}
