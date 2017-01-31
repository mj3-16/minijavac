package minijava.ir.assembler.operands;

/** An operand loaded from memory via a specified addressing mode. */
public class MemoryOperand extends Operand {

  public final AddressingMode mode;

  public MemoryOperand(OperandWidth width, AddressingMode mode) {
    super(width);
    this.mode = mode;
  }

  @Override
  public String toGNUAssembler() {
    return mode.toString();
  }

  @Override
  public String toString() {
    return toGNUAssembler();
  }
}
