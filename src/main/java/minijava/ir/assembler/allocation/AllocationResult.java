package minijava.ir.assembler.allocation;

import java.util.List;
import java.util.Map;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

public class AllocationResult {
  public final Map<LifetimeInterval, AMD64Register> allocation;
  public final Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes;

  public AllocationResult(
      Map<LifetimeInterval, AMD64Register> allocation,
      Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes) {
    this.allocation = allocation;
    this.splitLifetimes = splitLifetimes;
  }
}
