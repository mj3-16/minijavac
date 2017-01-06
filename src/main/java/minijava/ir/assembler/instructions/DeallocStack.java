package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.Register;

/** Abstraction of a <code>addq</code> instruction that free bytes of stack */
public class DeallocStack extends Instruction {

  public final int amount;

  public DeallocStack(int amount) {
    this.amount = amount;
    this.addComment(String.format("Free %d bytes of the stack", amount));
  }

  @Override
  public Type getType() {
    return Type.DEALLOC_STACK;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return super.createGNUAssemblerWoComments(new ConstArgument(amount), Register.STACK_POINTER);
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of();
  }
}
