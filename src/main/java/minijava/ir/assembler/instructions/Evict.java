package minijava.ir.assembler.instructions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import minijava.ir.assembler.location.Register;

/**
 * A meta instruction to force the temporary eviction of some registers. The registers may only be
 * reused by an allocator in instructions using NodeLocations
 */
public class Evict extends Instruction {

  public final List<Register> registers;

  public Evict(List<Register> registers) {
    this.registers = Collections.unmodifiableList(registers);
  }

  public Evict(Register... registers) {
    this(Arrays.asList(registers));
  }

  @Override
  public Type getType() {
    return Type.EVICT;
  }
}
