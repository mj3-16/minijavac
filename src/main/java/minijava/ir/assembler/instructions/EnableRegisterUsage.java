package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

/**
 * Meta instruction to enable the usage of some registers for allocation of {@link VirtualRegister}
 * objects.
 */
public class EnableRegisterUsage extends Instruction {

  public final List<AMD64Register> registers;

  public EnableRegisterUsage(List<AMD64Register> registers) {
    super(OperandWidth.Quad);
    this.registers = Collections.unmodifiableList(registers);
  }

  @Override
  public Type getType() {
    return Type.ENABLE_REGISTER_USAGE;
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
