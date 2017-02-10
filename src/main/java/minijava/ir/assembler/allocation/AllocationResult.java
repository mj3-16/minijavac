package minijava.ir.assembler.allocation;

import java.util.List;
import java.util.Map;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

public class AllocationResult {
  public final Map<LifetimeInterval, AMD64Register> allocation;
  public final Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes;
  public final Map<VirtualRegister, Integer> spillSlots;

  public AllocationResult(
      Map<LifetimeInterval, AMD64Register> allocation,
      Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes,
      Map<VirtualRegister, Integer> spillSlots) {
    this.allocation = allocation;
    this.splitLifetimes = splitLifetimes;
    this.spillSlots = spillSlots;
  }
}
