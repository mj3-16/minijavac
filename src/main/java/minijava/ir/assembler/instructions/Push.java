package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

/** Pushes a value on the stack */
public class Push extends UnaryInstruction {

  public Push(Operand arg) {
    super(arg);
  }

  @Override
  public Type getType() {
    return Type.PUSH;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
