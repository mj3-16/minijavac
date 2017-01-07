package minijava.ir.assembler.location;

import java.util.*;
import minijava.ir.assembler.instructions.Instruction;

/**
 * A temporary location that is used in the AssemblerGenerator and replaced with a real location by
 * a register allocator
 */
public class NodeLocation extends Location {

  public final int id;
  public final Optional<firm.nodes.Node> node;
  private Set<Instruction> usedBy;

  public NodeLocation(Register.Width width, int id, firm.nodes.Node node) {
    super(width);
    this.id = id;
    this.node = Optional.ofNullable(node);
    this.usedBy = new HashSet<>();
    if (this.node.isPresent()) {
      setComment(node);
    }
  }

  public NodeLocation(Register.Width width, int id) {
    this(width, id, null);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof NodeLocation && ((NodeLocation) obj).id == id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toGNUAssembler() {
    return String.format("{%d|%s}%s", id, width, formatComment());
  }

  public void addUsage(Instruction instruction) {
    usedBy.add(instruction);
  }

  public Set<Instruction> getUsages() {
    return Collections.unmodifiableSet(usedBy);
  }

  @Override
  public String toString() {
    return toGNUAssembler();
  }
}
