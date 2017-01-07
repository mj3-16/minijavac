package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.MemoryNodeLocation;

/** A meta instruction representing a {@link firm.nodes.Load} node */
public class MetaLoad extends Instruction {

  public final MemoryNodeLocation source;
  public final Argument destination;

  public MetaLoad(MemoryNodeLocation source, Argument destination) {
    super(getWidthOfArguments(MemoryNodeLocation.class, source, destination));
    this.source = source;
    this.destination = destination;
  }

  @Override
  public Type getType() {
    return Type.META_LOAD;
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of(source.address, destination);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
