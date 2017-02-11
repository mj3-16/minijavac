package minijava.ir.assembler.registers;

import static minijava.ir.utils.FirmUtils.modeToWidth;

import firm.nodes.Node;
import java.util.function.Function;
import minijava.ir.assembler.operands.OperandWidth;
import org.jetbrains.annotations.Nullable;

/**
 * A temporary register that is used in the AssemblerGenerator and replaced with a real register by
 * a register allocator
 */
public class VirtualRegister implements Register {

  public final int id;
  /** We need this for doing opaque reloads when spilling registers. */
  public final OperandWidth defWidth;
  /** Null iff it's a temporary. */
  @Nullable public final Node value;

  public VirtualRegister(int id, OperandWidth defWidth) {
    this.id = id;
    this.defWidth = defWidth;
    this.value = null;
  }

  public VirtualRegister(int id, Node value) {
    this.id = id;
    this.defWidth = modeToWidth(value.getMode());
    this.value = value;
  }

  @Override
  public <T> T match(
      Function<VirtualRegister, T> matchVirtual, Function<AMD64Register, T> matchHardware) {
    return matchVirtual.apply(this);
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
