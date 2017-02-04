package minijava.ir.assembler.operands;

import java.util.Objects;
import minijava.ir.utils.AssemblerUtils;

/** Constant operand for assembler instructions */
public class ImmediateOperand extends Operand {

  public final long value;

  public ImmediateOperand(OperandWidth width, long value) {
    super(width);
    this.value = value;
  }

  @Override
  public String toString() {
    return "ImmediateOperand{" + "value=" + value + '}';
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
    return AssemblerUtils.doesIntegerFitIntoImmPartOfInstruction(value);
  }

  @Override
  public Operand withChangedWidthImpl(OperandWidth width) {
    return new ImmediateOperand(width, this.value);
  }
}
