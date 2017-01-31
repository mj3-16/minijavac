package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import minijava.ir.assembler.location.Register;

/**
 * Meta instruction to disable the usage of some registers for allocation of {@link
 * minijava.ir.assembler.location.NodeLocation} objects.
 */
public class DisableRegisterUsage extends Instruction {

  public final List<Register> registers;

  public DisableRegisterUsage(List<Register> registers) {
    super(Register.Width.Quad);
    this.registers = Collections.unmodifiableList(registers);
  }

  @Override
  public Type getType() {
    return Type.DISABLE_REGISTER_USAGE;
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
