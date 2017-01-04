package minijava.ir.assembler.instructions;

import minijava.ir.assembler.location.Register;

/**
 * Abstraction of a <code>subq</code> instruction that allocates bytes on the stack for the
 * activation record
 */
public class AllocStack extends Instruction {

  public final int amount;

  public AllocStack(int amount) {
    this.amount = amount;
    this.addComment(String.format("Allocate %d bytes for the activation record", amount));
  }

  @Override
  public Type getType() {
    return Type.ALLOC_STACK;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return super.createGNUAssemblerWoComments(new ConstArgument(amount), Register.STACK_POINTER);
  }
}
