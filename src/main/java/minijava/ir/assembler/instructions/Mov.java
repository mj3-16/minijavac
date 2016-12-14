package minijava.ir.assembler.instructions;

import minijava.ir.assembler.location.Location;
import minijava.ir.assembler.location.Register;

/** Moves the source value into the destination */
public class Mov extends Instruction {

  public final Argument source;
  public final Location destination;

  public Mov(Argument source, Location destination) {
    this.source = source;
    this.destination = destination;
  }

  @Override
  public String toGNUAssembler() {
    return super.toGNUAssembler(source, destination);
  }

  @Override
  public Type getType() {
    return Type.MOV;
  }

  @Override
  protected Register.Width getWidthOfArguments() {
    return getMaxWithOfArguments(source, destination);
  }
}
