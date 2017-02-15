package minijava.backend.operands;

import java.util.Objects;
import java.util.function.Function;

/** Constant operand for assembler instructions */
public class ImmediateOperand extends Operand {

  public final long value;

  public ImmediateOperand(OperandWidth width, long value) {
    super(width);
    this.value = value;
  }

  @Override
  public String toString() {
    return String.format("imm%d{%s}", width.sizeInBytes * 8, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ImmediateOperand that = (ImmediateOperand) o;
    return width == that.width && value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, value);
  }

  public boolean fitsIntoImmPartOfInstruction() {
    return value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE;
  }

  @Override
  public Operand withChangedWidthImpl(OperandWidth width) {
    return new ImmediateOperand(width, this.value);
  }

  @Override
  public <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem) {
    return matchImm.apply(this);
  }
}
