package minijava.ir.assembler.location;

/** Location on the stack (relative to the base pointer) */
public class StackLocation extends Location {

  public final Register base;
  /** Offset to base pointer (in bytes) */
  public final int offset;

  public StackLocation(Register base, int offset) {
    this.base = base;
    this.offset = offset;
  }

  @Override
  public String toGNUAssembler() {
    return String.format("%d(%s)", offset, base.toGNUAssembler());
  }
}
