package minijava.backend.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.Operand;

public class Movs extends CodeBlockInstruction {
  public final Operand src;
  public final Operand dest;

  public Movs(Operand src, Operand dest) {
    super(newArrayList(src), newArrayList(dest));
    checkArgument(src.width.sizeInBytes * 2 == dest.width.sizeInBytes, "Movsx doubles width");
    this.src = src;
    this.dest = dest;
    setHints(src, dest);
    setMayBeMemory(src);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
