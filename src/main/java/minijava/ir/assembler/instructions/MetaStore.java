package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.MemoryNodeLocation;

/** A meta instruction representing a {@link firm.nodes.Store} node */
public class MetaStore extends Instruction {

  public final Argument source;
  public final MemoryNodeLocation destination;

  public MetaStore(Argument source, MemoryNodeLocation destination) {
    this.source = source;
    this.destination = destination;
  }

  @Override
  public Type getType() {
    return Type.META_STORE;
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of(source, destination.address);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
