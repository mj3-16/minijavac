package minijava.backend.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import org.jooq.lambda.Seq;

public class IDiv extends CodeBlockInstruction {

  public final Operand divisor;

  public IDiv(Operand divisor) {
    super(
        newArrayList(
            new RegisterOperand(divisor.width, AMD64Register.A),
            new RegisterOperand(divisor.width, AMD64Register.D),
            divisor),
        toOperands(divisor.width, Seq.of(AMD64Register.A, AMD64Register.D)));
    this.divisor = divisor;
    setMayBeMemory(divisor);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
