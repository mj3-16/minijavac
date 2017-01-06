package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class Call extends Instruction {

  public final String targetLdName;

  public Call(String targetLdName) {
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
}
