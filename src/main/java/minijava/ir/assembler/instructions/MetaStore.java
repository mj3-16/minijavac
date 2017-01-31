package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.MemoryNodeLocation;

/** A meta instruction representing a {@link firm.nodes.Store} node */
public class MetaStore extends Instruction {

  public final Operand source;
  public final MemoryNodeLocation destination;

  public MetaStore(Operand source, MemoryNodeLocation destination) {
    super(getWidthOfArguments(MetaStore.class, source, destination));
    this.source = source;
    this.destination = destination;
  }

  @Override
  public Type getType() {
    return Type.META_STORE;
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of(source, destination.address);
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
