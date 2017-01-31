package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;

/** <code>ret</code> instruction that returns from a function call */
public class Ret extends Instruction {
  public Ret() {
    super(OperandWidth.Quad);
  }

  @Override
  public Type getType() {
    return Type.RET;
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
