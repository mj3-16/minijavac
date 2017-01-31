package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;

public class Call extends Instruction {

  public final String targetLdName;

  public Call(OperandWidth resultWidth, String targetLdName) {
    super(resultWidth);
    this.targetLdName = targetLdName;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return super.toGNUAssemblerWoComments() + " " + targetLdName;
  }

  @Override
  public Type getType() {
    return Type.CALL;
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
