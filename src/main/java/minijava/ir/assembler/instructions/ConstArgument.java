package minijava.ir.assembler.instructions;

import minijava.ir.assembler.location.Register;

/** Constant argument for assembler instructions */
public class ConstArgument extends Argument {

  public final String value;

  public ConstArgument(Register.Width width, long value) {
    super(width);
    this.value = "$" + value;
  }

  /** @param value has to start with "$", "0x", ... */
  public ConstArgument(Register.Width width, String value) {
    super(width);
    this.value = value;
  }

  @Override
  public String toGNUAssembler() {
    return value;
  }

  @Override
  public String toString() {
    return String.format("Const(%s)", value);
  }

  public ConstArgument toWidth(Register.Width newWidth) {
    return new ConstArgument(newWidth, value);
  }
}
