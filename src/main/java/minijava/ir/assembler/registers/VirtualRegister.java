package minijava.ir.assembler.registers;

import firm.nodes.Node;
import org.jetbrains.annotations.Nullable;

/**
 * A temporary register that is used in the AssemblerGenerator and replaced with a real register by
 * a register allocator
 */
public class VirtualRegister implements Register {

  public final int id;
  /** Null iff it's a temporary. */
  @Nullable public final Node value;

  @Nullable public AMD64Register constraint;

  public VirtualRegister(int id) {
    this.id = id;
    this.value = null;
  }

  public VirtualRegister(int id, Node value) {
    this.id = id;
    this.value = value;
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
    return "%" + id;
  }
}
