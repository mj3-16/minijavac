package minijava.ir.assembler.block;

import firm.nodes.Phi;
import java.util.List;
import java.util.Objects;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.Register;

public class PhiFunction {
  public final List<? extends Register> arguments;
  public final Register result;
  public final OperandWidth width;
  /** We need this just for equality and hashing.Register */
  private final Phi phi;

  public PhiFunction(
      List<? extends Register> arguments, Register result, OperandWidth width, Phi phi) {
    this.arguments = arguments;
    this.result = result;
    this.width = width;
    this.phi = phi;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PhiFunction that = (PhiFunction) o;
    return Objects.equals(phi, that.phi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(phi);
  }
}
