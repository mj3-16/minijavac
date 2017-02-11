package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import org.jooq.lambda.Seq;

public class IDiv extends Instruction {

  public final Operand divisor;

  public IDiv(Operand divisor) {
    super(
        newArrayList(new RegisterOperand(divisor.width, AMD64Register.A), divisor),
        toOperands(divisor.width, Seq.of(AMD64Register.A, AMD64Register.D)));
    this.divisor = divisor;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
