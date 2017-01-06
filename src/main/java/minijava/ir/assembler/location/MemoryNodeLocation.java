package minijava.ir.assembler.location;

import firm.nodes.Node;
import minijava.ir.assembler.instructions.Argument;

/** Location that is based on a variable address value. */
public class MemoryNodeLocation extends NodeLocation {

  public final Argument address;

  public MemoryNodeLocation(Register.Width width, int id, Node node, Argument address) {
    super(width, id, node);
    this.address = address;
  }

  @Override
  public String toGNUAssembler() {
    return String.format("{%d|%s|%s}", id, width, address.toGNUAssembler());
  }
}
