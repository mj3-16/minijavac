package minijava.ir.assembler.location;

/** Location in the memory (relative to the base pointer) */
public class RegRelativeLocation extends Location {

  public final Register base;
  /** Offset to base pointer (in bytes) */
  public final Integer offset;

  public RegRelativeLocation(Register.Width width, Register base, int offset) {
    super(width);
    this.base = base;
    this.offset = offset;
  }

  @Override
  public String toGNUAssembler() {
    return String.format("%d(%s)", offset, base.toGNUAssembler());
  }
}
