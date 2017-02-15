package minijava.backend.lifetime;

import java.util.Map;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;

public class LifetimeAnalysisResult {
  public final Map<VirtualRegister, LifetimeInterval> virtualIntervals;
  public final Map<AMD64Register, FixedInterval> fixedIntervals;

  LifetimeAnalysisResult(
      Map<VirtualRegister, LifetimeInterval> virtualIntervals,
      Map<AMD64Register, FixedInterval> fixedIntervals) {
    this.virtualIntervals = virtualIntervals;
    this.fixedIntervals = fixedIntervals;
  }
}
