package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

/** <code>ret</code> instruction that returns from a function call */
public class Ret extends Instruction {
  @Override
  public Type getType() {
    return Type.RET;
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of();
  }
}
