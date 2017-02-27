package minijava.backend.operands;

import firm.nodes.Node;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** An operand loaded from memory via a specified addressing mode. */
public class MemoryOperand extends Operand {

  public final AddressingMode mode;

  public MemoryOperand(OperandWidth width, AddressingMode mode) {
    super(width);
    this.mode = mode;
  }

  public MemoryOperand(Node irNode, AddressingMode mode) {
    super(irNode);
    this.mode = mode;
  }

  @Override
  public Operand withChangedNode(Node irNode) {
    return new MemoryOperand(irNode, this.mode);
  }

  @Override
  public <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem) {
    return matchMem.apply(this);
  }

  @Override
  public Set<Use> reads(boolean inOutputPosition, boolean mayBeMemoryAccess) {
    Set<Use> reads = new HashSet<>();
    if (mode.base != null) {
      reads.add(new Use(mode.base, false));
    }
    if (mode.index != null) {
      reads.add(new Use(mode.index, false));
    }
    return reads;
  }

  @Nullable
  @Override
  public Use writes(boolean mayBeMemoryAccess) {
    return null;
  }

  @Override
  public String toString() {
    return String.format("m%d{%s}", width.sizeInBytes * 8, mode);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MemoryOperand that = (MemoryOperand) o;
    return width == that.width && Objects.equals(mode, that.mode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, mode);
  }
}
