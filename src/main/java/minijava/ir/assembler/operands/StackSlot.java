package minijava.ir.assembler.operands;

import java.util.Objects;
import minijava.ir.assembler.registers.AMD64Register;

/** A registers relative to the base pointer */
public class StackSlot extends MemoryOperand {

  public StackSlot(OperandWidth width, int offset) {
    super(width, AddressingMode.offsetFromRegister(AMD64Register.BASE_POINTER, offset));
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
    return Objects.equals(mode, that.mode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mode);
  }
}
