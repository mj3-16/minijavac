package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.AMD64Register;
import org.jooq.lambda.Seq;

public class CLTD extends Instruction {
  public CLTD(OperandWidth width) {
    super(
        toOperands(width, Seq.of(AMD64Register.A)),
        toOperands(width, Seq.of(AMD64Register.A, AMD64Register.D)));
  }
}
