package minijava.backend.operands;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Sets;
import firm.nodes.Node;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import minijava.backend.registers.Register;

public class RegisterOperand extends Operand {

  public final Register register;

  public RegisterOperand(OperandWidth width, Register register) {
    super(width);
    checkArgument(register != null, "register was null");
    this.register = register;
  }

  public RegisterOperand(Node irNode, Register register) {
    super(irNode);
    checkArgument(register != null, "register was null");
    this.register = register;
  }

  @Override
  public Operand withChangedNode(Node irNode) {
    return new RegisterOperand(irNode, register);
  }

  @Override
  public <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem) {
    return matchReg.apply(this);
  }

  @Override
  public Set<Use> reads(boolean inOutputPosition, boolean mayBeMemoryAccess) {
    return inOutputPosition
        ? Sets.newHashSet()
        : Sets.newHashSet(new Use(register, mayBeMemoryAccess));
  }

  @Override
  public Use writes(boolean mayBeMemoryAccess) {
    return new Use(register, mayBeMemoryAccess);
  }

  @Override
  public String toString() {
    return String.format("r%d{%s}", width.sizeInBytes * 8, register);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RegisterOperand that = (RegisterOperand) o;
    return width == that.width && Objects.equals(register, that.register);
  }

  @Override
  public int hashCode() {
    return Objects.hash(width, register);
  }
}
