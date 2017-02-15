package minijava.backend;

import minijava.backend.registers.VirtualRegister;

public class VirtualRegisterSupply {
  private int nextFreeId = 0;

  public VirtualRegister next() {
    return new VirtualRegister(nextFreeId++, null);
  }
}
