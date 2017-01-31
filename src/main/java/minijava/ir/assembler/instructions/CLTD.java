package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;

/**
 * Convert Signed Long to Signed Double Long
 *
 * <p>Sign-extends EAX, resulting in EDX:EAX
 */
public class CLTD extends Instruction {

  public CLTD() {
    super(OperandWidth.Long);
  }

  @Override
  public Type getType() {
    return Type.CLTD;
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of();
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
