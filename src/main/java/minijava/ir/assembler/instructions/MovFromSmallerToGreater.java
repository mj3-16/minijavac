package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.registers.Register;

/**
 * The destination may have a greater width than the source register. Be sure to zero the
 * destination register before.
 */
public class MovFromSmallerToGreater extends Instruction {

  public final Operand source;
  public final Register destination;

  public MovFromSmallerToGreater(Operand source, Register destination) {
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
  public List<Operand> getArguments() {
    return ImmutableList.of(source, destination);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
