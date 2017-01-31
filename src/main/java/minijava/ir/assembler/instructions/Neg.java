package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

/** Negates the passed argument and stores it back */
public class Neg extends UnaryInstruction {

  public Neg(Operand arg) {
    super(arg);
  }

  @Override
  public Type getType() {
    return Type.NEG;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
