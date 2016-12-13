package minijava.ir.assembler.instructions;

/** Integer constant argument for assembler instructions */
public class ConstArgument implements Argument {

  public final int value;

  public ConstArgument(int value) {
    this.value = value;
  }

  @Override
  public String toGNUAssembler() {
    return String.format("$%d", value);
  }
}
