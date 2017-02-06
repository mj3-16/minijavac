package minijava.ir.assembler.operands;

import java.util.Objects;
import minijava.ir.assembler.registers.Register;

public class RegisterOperand extends Operand {

  public final Register register;

  public RegisterOperand(OperandWidth width, Register register) {
    super(width);
    this.register = register;
  }

  @Override
  Operand withChangedWidthImpl(OperandWidth width) {
    return new RegisterOperand(width, register);
  }

  @Override
  public String toString() {
    return String.format("r%d{%s}", width.sizeInBytes * 8, register);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RegisterOperand that = (RegisterOperand) o;
    return width == that.width && Objects.equals(register, that.register);
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, register);
  }
}
