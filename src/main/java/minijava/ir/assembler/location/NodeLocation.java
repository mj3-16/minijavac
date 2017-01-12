package minijava.ir.assembler.location;

import java.util.*;

/**
 * A temporary location that is used in the AssemblerGenerator and replaced with a real location by
 * a register allocator
 */
public class NodeLocation extends Location {

  public final int id;
  public final Optional<firm.nodes.Node> node;

  public NodeLocation(Register.Width width, int id, firm.nodes.Node node) {
    super(width);
    this.id = id;
    this.node = Optional.ofNullable(node);
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

  @Override
  public String toString() {
    return toGNUAssembler();
  }
}
