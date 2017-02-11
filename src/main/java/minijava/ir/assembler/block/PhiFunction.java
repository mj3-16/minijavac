package minijava.ir.assembler.block;

import static com.google.common.collect.Lists.newArrayList;
import static org.jooq.lambda.Seq.seq;

import firm.nodes.Phi;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class PhiFunction extends Instruction {
  public final Map<CodeBlock, Operand> inputs;
  public final Operand output;
  /** We need this just for equality and hashing. */
  public final Phi phi;

  private PhiFunction(Map<CodeBlock, Operand> inputs, Operand output, Phi phi) {
    super(new ArrayList<>(inputs.values()), newArrayList(output));
    this.inputs = inputs;
    this.output = output;
    this.phi = phi;
    setHints(seq(inputs.values()).append(output));
  }

  public PhiFunction(Map<CodeBlock, Operand> inputs, RegisterOperand output, Phi phi) {
    this(inputs, (Operand) output, phi);
  }

  public PhiFunction(Map<CodeBlock, Operand> inputs, MemoryOperand output, Phi phi) {
    this(inputs, (Operand) output, phi);
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
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
