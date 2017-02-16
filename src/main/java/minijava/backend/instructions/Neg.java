package minijava.backend.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.backend.operands.Operand;

public class Neg extends CodeBlockInstruction {
  public final Operand inout;

  public Neg(Operand inout) {
    super(newArrayList(inout), newArrayList(inout));
    this.inout = inout;
    setMayBeMemory(inout);
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
