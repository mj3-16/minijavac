package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.MemoryNodeLocation;

/** A meta instruction representing a {@link firm.nodes.Load} node */
public class MetaLoad extends Instruction {

  public final MemoryNodeLocation source;
  public final Operand destination;

  public MetaLoad(MemoryNodeLocation source, Operand destination) {
    super(getWidthOfArguments(MemoryNodeLocation.class, source, destination));
    this.source = source;
    this.destination = destination;
  }

  @Override
  public Type getType() {
    return Type.META_LOAD;
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of(source.address, destination);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(source, destination);
  }
}
