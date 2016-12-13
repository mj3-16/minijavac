package minijava.ir.assembler.location;

/** Location on the stack (relative to the base pointer) */
public class StackLocation extends Location {

  /** Offset to base pointer (in bytes) */
  public final int offset;

  public StackLocation(int offset) {
    this.offset = offset;
  }

  @Override
  public String toGNUAssembler() {
    return String.format("%d(%%rbp)", offset);
  }
}
