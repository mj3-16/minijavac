package minijava.ir.assembler.operands;

/** Operand for an assembler instruction */
public abstract class Operand {
  public final OperandWidth width;

  public Operand(OperandWidth width) {
    this.width = width;
  }
}
