package minijava.ir.assembler.instructions;

/** Constant argument for assembler instructions */
public class ConstArgument implements Argument {

  public final String value;

  public ConstArgument(int value) {
    this.value = "$" + value;
  }

  /** @param value has to start with "$", "0x", ... */
  public ConstArgument(String value) {
    this.value = value;
  }

  @Override
  public String toGNUAssembler() {
    return value;
  }
}
