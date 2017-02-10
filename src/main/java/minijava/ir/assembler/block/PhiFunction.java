package minijava.ir.assembler.block;

import firm.nodes.Phi;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.Register;

public class PhiFunction {
  public final Map<CodeBlock, ? extends Register> inputs;
  public final Register output;
  public final OperandWidth width;
  /** We need this just for equality and hashing. */
  private final Phi phi;

  public PhiFunction(
      Map<CodeBlock, ? extends Register> inputs, Register output, OperandWidth width, Phi phi) {
    this.inputs = inputs;
    this.output = output;
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

  @Override
  public String toString() {
    return "Phi" + width.sizeInBytes * 8 + inputs + "-> " + output;
  }

  public Set<Register> registerHints() {
    Set<Register> hints = new HashSet<>(inputs.values());
    hints.add(output);
    return hints;
  }
}
