package minijava.backend.deconstruction;

import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;

class Move {

  public Operand src;
  public Operand dest;

  public Move(Operand src, Operand dest) {
    this.src = src;
    this.dest = dest;
  }

  public boolean isMemToMem() {
    return dest instanceof MemoryOperand && src instanceof MemoryOperand;
  }

  public boolean isRegToReg() {
    return dest instanceof RegisterOperand && src instanceof RegisterOperand;
  }

  public boolean isNoop() {
    return src.equals(dest);
  }

  @Override
  public String toString() {
    return src + " -> " + dest;
  }
}
