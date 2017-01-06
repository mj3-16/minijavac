package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Method instruction that should be replaced by a method prologue:
 *
 * <p>prepended.add(new Push(Register.BASE_POINTER).com("Backup old base pointer")); prepended.add(
 * new Mov(Register.STACK_POINTER, Register.BASE_POINTER) .com("Set base pointer for new activation
 * record")); prepended.add(new AllocStack(allocator.getActivationRecordSize()));
 */
public class MethodPrologue extends Instruction {

  @Override
  public Type getType() {
    return Type.PROLOGUE;
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
