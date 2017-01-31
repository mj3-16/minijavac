package minijava.ir.assembler.instructions;

import minijava.ir.assembler.operands.Operand;

/**
 * Important note: It compares the right argument with the left argument. Be sure to swap its
 * arguments if needed.
 *
 * <p>Why? Because the GNU assembler swaps the arguments of many instructions compared to the Intel
 * assembler.
 */
public class Cmp extends BinaryInstruction {
  public Cmp(Operand left, Operand right) {
    super(left, right);
  }

  @Override
  public Type getType() {
    return Type.CMP;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
