package minijava.backend.operands;

import com.google.common.collect.Sets;
import firm.nodes.Node;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/** Constant operand for assembler instructions */
public class ImmediateOperand extends Operand {

  public final long value;

  public ImmediateOperand(OperandWidth width, long value) {
    super(width);
    this.value = value;
  }

  public ImmediateOperand(Node irNode, long value) {
    super(irNode);
    this.value = value;
  }

  @Override
  public String toString() {
    return String.format("imm%d{%s}", width.sizeInBytes * 8, value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ImmediateOperand that = (ImmediateOperand) o;
    return width == that.width && value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, value);
  }

  public boolean fitsIntoImmPartOfInstruction() {
    return value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE;
  }

  @Override
  public Operand withChangedNode(Node irNode) {
    return new ImmediateOperand(irNode, this.value);
  }

  @Override
  public <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem) {
    return matchImm.apply(this);
  }

  @Override
  public Set<Use> reads(boolean inOutputPosition, boolean mayBeMemoryAccess) {
    return Sets.newHashSet();
  }

  @Nullable
  @Override
  public Use writes(boolean mayBeMemoryAccess) {
    return null;
  }
}
