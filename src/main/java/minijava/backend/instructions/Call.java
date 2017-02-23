package minijava.backend.instructions;

import static org.jooq.lambda.Seq.seq;

import java.util.List;
import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;

public class Call extends CodeBlockInstruction {
  public String label;

  public Call(String label, List<Operand> arguments) {
    super(arguments, toOperands(AMD64Register.ALLOCATABLE));
    this.label = label;
    assert seq(arguments)
        .ofType(RegisterOperand.class)
        .noneMatch(reg -> reg.register instanceof VirtualRegister);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
