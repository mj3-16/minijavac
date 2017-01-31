package minijava.ir.assembler.operands;

/** Meta registers for a par */
public class Parameter extends Operand {

  public final int paramNumber;

  public Parameter(OperandWidth width, int paramNumber) {
    super(width);
    this.paramNumber = paramNumber;
  }

  @Override
  public String toString() {
    return String.format("{Param %d|%s}", paramNumber, width);
  }

  @Override
  public String toGNUAssembler() {
    return toString();
  }
}
