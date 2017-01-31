package minijava.ir.assembler.operands;

public class Immediate extends AddressingMode {

  public final long value;

  public Immediate(long value, OperandWidth width) {
    super(width);
    this.value = value;
  }
}
