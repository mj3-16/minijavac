package minijava.ir.assembler.operands;

import static com.google.common.base.Preconditions.checkArgument;

import com.beust.jcommander.internal.Nullable;
import java.util.Objects;
import minijava.ir.assembler.registers.Register;

public class AddressingMode {
  public final int displacement;
  @Nullable public final Register base;
  @Nullable public final Register index;
  public final int scale;

  public AddressingMode(Register base, Register index, int scale) {
    this(0, base, index, scale);
  }

  public AddressingMode(int displacement, Register index, int scale) {
    this(displacement, null, index, scale);
  }

  public AddressingMode(int displacement, Register base, Register index, int scale) {
    checkArgument(scale == 0 || isValidScale(scale), "Invalid scale: %s", scale);
    checkArgument(scale == 0 || index != null, "No index register, but non-zero scale: %s", scale);
    checkArgument(
        displacement != 0 || base != null, "One of displacement and base have to be non-null");
    this.displacement = displacement;
    this.base = base;
    this.index = index;
    this.scale = scale;
  }

  public AddressingMode(int displacement, Register base) {
    this(displacement, base, null, 0);
  }

  public static AddressingMode atRegister(Register base) {
    return new AddressingMode(0, base, null, 0);
  }

  public static AddressingMode offsetFromRegister(Register base, int offset) {
    return new AddressingMode(offset, base, null, 0);
  }

  private static boolean isValidScale(int scale) {
    return scale == 1 || scale == 2 || scale == 4 || scale == 8;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (displacement != 0) {
      builder.append(displacement);
    }
    boolean hasBase = base != null;
    boolean hasIndex = index != null;
    if (hasBase || hasIndex) {
      builder.append('(');
      if (hasBase) {
        builder.append(base);
      }
      if (hasIndex) {
        builder.append(',');
        builder.append(index);
        builder.append(',');
        builder.append(scale);
      }
      builder.append(')');
    }
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AddressingMode that = (AddressingMode) o;
    return displacement == that.displacement
        && scale == that.scale
        && Objects.equals(base, that.base)
        && Objects.equals(index, that.index);
  }

  @Override
  public int hashCode() {
    return Objects.hash(displacement, base, index, scale);
  }
}
