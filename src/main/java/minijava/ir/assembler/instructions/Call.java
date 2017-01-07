package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.Register;

public class Call extends Instruction {

  public final String targetLdName;

  public Call(Register.Width resultWidth, String targetLdName) {
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
  public List<Argument> getArguments() {
    return ImmutableList.of();
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
