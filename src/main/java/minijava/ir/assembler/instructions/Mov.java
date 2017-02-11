package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Mov extends Instruction {
  public final Operand src;
  public final Operand dest;

  public Mov(Operand src, MemoryOperand dest) {
    super(newArrayList(src, dest), newArrayList(dest));
    assert src.width == dest.width;
    this.src = src;
    this.dest = dest;
  }

  public Mov(Operand src, RegisterOperand dest) {
    super(newArrayList(src), newArrayList(dest));
    assert src.width == dest.width;
    this.src = src;
    this.dest = dest;
    setHints(src, dest);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
