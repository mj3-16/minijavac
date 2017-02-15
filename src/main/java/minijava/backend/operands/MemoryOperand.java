package minijava.backend.operands;

import java.util.Objects;
import java.util.function.Function;

/** An operand loaded from memory via a specified addressing mode. */
public class MemoryOperand extends Operand {

  public final AddressingMode mode;

  public MemoryOperand(OperandWidth width, AddressingMode mode) {
    super(width);
    this.mode = mode;
  }

  @Override
  Operand withChangedWidthImpl(OperandWidth width) {
    return new MemoryOperand(width, mode);
  }

  @Override
  public <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem) {
    return matchMem.apply(this);
  }

  @Override
  public String toString() {
    return String.format("m%d{%s}", width.sizeInBytes * 8, mode);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MemoryOperand that = (MemoryOperand) o;
    return width == that.width && Objects.equals(mode, that.mode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, mode);
  }
}
