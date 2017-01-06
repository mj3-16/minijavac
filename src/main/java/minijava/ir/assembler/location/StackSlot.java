package minijava.ir.assembler.location;

/** A location relative to the base pointer */
public class StackSlot extends RegRelativeLocation {

  public StackSlot(Register.Width width, int offset) {
    super(width, Register.BASE_POINTER, offset);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof StackSlot && ((StackSlot) obj).offset == offset;
  }
}
