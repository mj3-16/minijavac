package minijava.backend.instructions;

import minijava.backend.operands.OperandWidth;
import minijava.backend.registers.AMD64Register;
import org.jooq.lambda.Seq;

public class Cqto extends CodeBlockInstruction {
  public Cqto(OperandWidth width) {
    super(
        toOperands(width, Seq.of(AMD64Register.A)),
        toOperands(width, Seq.of(AMD64Register.A, AMD64Register.D)));
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
