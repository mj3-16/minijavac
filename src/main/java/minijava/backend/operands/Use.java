package minijava.backend.operands;

import minijava.backend.registers.Register;

public class Use {
  public final Register register;
  public final boolean mayBeReplacedByMemoryAccess;

  public Use(Register register, boolean mayBeReplacedByMemoryAccess) {
    this.register = register;
    this.mayBeReplacedByMemoryAccess = mayBeReplacedByMemoryAccess;
  }
}
