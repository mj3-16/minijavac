package minijava.ir.assembler.instructions;

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
  public String toGNUAssembler() {
    return super.toGNUAssembler(new ConstArgument(amount), Register.STACK_POINTER);
  }
}
