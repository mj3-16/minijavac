package minijava.ir.assembler.block;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static org.jooq.lambda.Seq.seq;

import firm.nodes.Phi;
import java.util.*;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;
import org.jooq.lambda.Seq;

public class PhiFunction extends Instruction {
  public final Map<CodeBlock, Operand> inputs;
  public final Operand output;
  /** We need this just for equality and hashing. */
  public final Phi phi;

  public PhiFunction(Map<CodeBlock, Operand> inputs, Operand output, Phi phi) {
    super(new ArrayList<>(inputs.values()), newArrayList(output));
    checkArgument(
        !(output instanceof ImmediateOperand), "The output of a Phi can't be an immediate");
    this.inputs = inputs;
    this.output = output;
    this.phi = phi;
    setHints(seq(inputs.values()).append(output));
  }

  public Set<Register> registerHints(CodeBlock pred) {
    return Seq.of(output, inputs.get(pred))
        .ofType(RegisterOperand.class)
        .map(reg -> reg.register)
        .toSet();
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
