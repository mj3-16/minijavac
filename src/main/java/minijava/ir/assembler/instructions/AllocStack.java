package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.location.Register;

/**
 * Abstraction of a <code>subq</code> instruction that allocates bytes on the stack for the
 * activation record
 */
public class AllocStack extends Instruction {

  public final int amount;

  public AllocStack(int amount) {
    super(Register.Width.Quad);
    this.amount = amount;
    this.addComment(String.format("Allocate %d bytes for the activation record", amount));
  }

  @Override
  public Type getType() {
    return Type.ALLOC_STACK;
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of();
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return super.createGNUAssemblerWoComments(
        new ConstOperand(Register.Width.Quad, amount), Register.STACK_POINTER);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
