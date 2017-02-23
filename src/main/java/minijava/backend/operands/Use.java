package minijava.backend.operands;

import com.google.common.base.Preconditions;
import minijava.backend.registers.Register;

public class Use {
  public final Register register;
  public final boolean mayBeReplacedByMemoryAccess;

  public Use(Register register, boolean mayBeReplacedByMemoryAccess) {
    Preconditions.checkArgument(register != null, "A use must specify a register");
    this.register = register;
    this.mayBeReplacedByMemoryAccess = mayBeReplacedByMemoryAccess;
  }
}
