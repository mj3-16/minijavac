package minijava.ir.assembler.lifetime;

import java.util.List;
import java.util.Map;
import minijava.ir.assembler.registers.AMD64Register;

public class LifetimeAnalysisResult {
  public final List<LifetimeInterval> virtualIntervals;
  public final Map<AMD64Register, FixedInterval> fixedIntervals;

  public LifetimeAnalysisResult(
      List<LifetimeInterval> virtualIntervals, Map<AMD64Register, FixedInterval> fixedIntervals) {
    this.virtualIntervals = virtualIntervals;
    this.fixedIntervals = fixedIntervals;
  }
}
