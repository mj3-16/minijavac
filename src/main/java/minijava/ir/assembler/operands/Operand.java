package minijava.ir.assembler.operands;

/** Operand for an assembler instruction */
public abstract class Operand {
  public final OperandWidth width;

  public Operand(OperandWidth width) {
    this.width = width;
  }

  public Operand withChangedWidth(OperandWidth width) {
    if (this.width == width) {
      return this;
    }
    return withChangedWidthImpl(width);
  }

  abstract Operand withChangedWidthImpl(OperandWidth width);
}
