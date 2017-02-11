package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Test extends Instruction {
  public final Operand left;
  public final Operand right;
  // We could also handle the FLAGS register as constraints, I guess... But this gets awkward
  // with spilling and getting operands and stuff.

  public Test(RegisterOperand left, Operand right) {
    super(newArrayList(left, right), newArrayList());
    this.left = left;
    this.right = right;
  }

  public Test(MemoryOperand left, Operand right) {
    super(newArrayList(left, right), newArrayList());
    this.left = left;
    this.right = right;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
