package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Pop extends Instruction {
  public final Operand output;

  public Pop(RegisterOperand output) {
    super(newArrayList(), newArrayList(output));
    this.output = output;
  }

  public Pop(MemoryOperand output) {
    super(newArrayList(), newArrayList(output));
    this.output = output;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
