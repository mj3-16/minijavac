package minijava.ir.assembler.location;

/** Meta location for a par */
public class ParamLocation extends NodeLocation {

  public final int paramNumber;

  public ParamLocation(Register.Width width, int id, int paramNumber) {
    super(width, id);
    this.paramNumber = paramNumber;
    setComment(String.format("Param %d", paramNumber));
  }
}
