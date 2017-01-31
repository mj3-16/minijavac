package minijava.ir.assembler.registers;

/**
 * A temporary register that is used in the AssemblerGenerator and replaced with a real register by
 * a register allocator
 */
public class VirtualRegister extends Register {

  public final int id;

  public VirtualRegister(int id, firm.nodes.Node def) {
    this.id = id;
    if (def != null) {
      setComment(def);
    }
  }

  public VirtualRegister(int id) {
    this(id, null);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof VirtualRegister && ((VirtualRegister) obj).id == id;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(id);
  }

  @Override
  public String toString() {
    return String.format("%%%d %s", id, formatComment());
  }
}
