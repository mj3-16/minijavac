package minijava.ir.assembler.operands;

public enum OperandWidth {
  Byte("b", 1),
  /** 32 bit */
  Long("l", 4),
  /** 64 bit */
  Quad("q", 8);

  /** Suffix of instructions like <code>mov</code> that work with registers of this size */
  public final String asm;

  public final int sizeInBytes;

  OperandWidth(String asm, int sizeInBytes) {
    this.asm = asm;
    this.sizeInBytes = sizeInBytes;
  }
}
