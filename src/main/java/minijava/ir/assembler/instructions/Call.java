package minijava.ir.assembler.instructions;

import static org.jooq.lambda.Seq.seq;

import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

public class Call extends CodeBlockInstruction {
  public String label;

  public Call(String label, List<Operand> arguments) {
    super(arguments, toOperands(AMD64Register.allocatable));
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
