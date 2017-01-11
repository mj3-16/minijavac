package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.Location;

/**
 * The destination may have a greater width than the source register. Be sure to zero the
 * destination register before.
 */
public class MovFromSmallerToGreater extends Instruction {

  public final Argument source;
  public final Location destination;

  public MovFromSmallerToGreater(Argument source, Location destination) {
    super(source.width);
    if (source.width.ordinal() > destination.width.ordinal()) {
      throw new RuntimeException(
          String.format("Destination has width %s < source width %s", destination, source));
    }
    this.source = source;
    this.destination = destination;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(source, destination);
  }

  @Override
  public Type getType() {
    return Type.MOVSG;
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of(source, destination);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
