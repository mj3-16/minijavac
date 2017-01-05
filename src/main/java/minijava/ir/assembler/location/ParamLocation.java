package minijava.ir.assembler.location;

/** Meta location for a par */
public class ParamLocation extends NodeLocation {

  public final int paramNumber;

  public ParamLocation(int id, Register.Width width, int paramNumber) {
    super(id, width);
    this.paramNumber = paramNumber;
    setComment(String.format("Param %d", paramNumber));
  }
}
