package minijava.backend.allocation;

import static minijava.backend.registers.AMD64Register.A;
import static minijava.backend.registers.AMD64Register.B;
import static minijava.backend.registers.AMD64Register.DI;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import minijava.backend.ExampleProgram;
import minijava.backend.lifetime.BlockPosition;
import minijava.backend.lifetime.LifetimeAnalysis;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;
import org.junit.Assert;
import org.junit.Test;

public class LinearScanRegisterAllocationTest {
  private static Set<AMD64Register> ONE_REG = Sets.newHashSet(DI);
  private static Set<AMD64Register> TWO_REGS = Sets.newHashSet(A, DI);
  private static Set<AMD64Register> THREE_REGS = Sets.newHashSet(A, B, DI);

  @Test
  public void loopCountingToFiveThreeRegs_doesntSpill() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, THREE_REGS);

    assertLifetimesMatch(lifetimes, result);

    assertNumberOfSpills(result, 0);
  }

  @Test
  public void loopCountingToFiveTwoRegs_spills() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, TWO_REGS);

    result.printDebugInfo();

    assertLifetimesMatch(lifetimes, result);

    // r0 is the constant 5, which is the argument to the Cmp
    VirtualRegister r0 = example.registers.get(0);
    // I'm afraid that testing for specific splits is too brittle.
    //assertIsReloadedOnce(result, r0);

    // r2 is the constant 1, which is the argument to the Add instruction in the loop
    VirtualRegister r2 = example.registers.get(2);
    //assertIsReloadedOnce(result, r2);

    // All other registers shouldn't have been split.
    assertNumberOfSpills(result, 2);
  }

  private void assertNumberOfSpills(AllocationResult result, int spills) {
    Assert.assertThat(
        "Shouldn't have split more than " + spills + " registers",
        result.spillSlots.size(),
        is(lessThanOrEqualTo(spills)));
  }

  @Test
  public void loopCountingToFiveOneReg_spills() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, ONE_REG);

    result.printDebugInfo();

    assertLifetimesMatch(lifetimes, result);

    // Not really sure what to verify. There are many ways this has a correct outcome, most of which
    // are not simply stated.
    assertNumberOfSpills(result, 4);
  }

  private void assertLifetimesMatch(LifetimeAnalysisResult lifetimes, AllocationResult result) {
    for (List<LifetimeInterval> splits : result.splitLifetimes.values()) {
      for (int i = 0; i < splits.size() - 1; ++i) {
        LifetimeInterval prev = splits.get(i);
        LifetimeInterval next = splits.get(i + 1);
        Assert.assertThat(
            "Splits of the same register may not overlap",
            prev.ranges.firstIntersectionWith(next.ranges),
            nullValue());
        BlockPosition endPrev = prev.ranges.to();
        BlockPosition startNext = next.ranges.from();
        Assert.assertTrue(
            "Splits of the same register may not have holes",
            !endPrev.block.equals(startNext.block) || endPrev.pos + 1 == startNext.pos);

        Assert.assertTrue(
            "Consecutive splits which are spilled should be merged",
            result.allocation.get(prev) != null || result.allocation.get(next) != null);
      }

      LifetimeInterval first = splits.get(0);
      LifetimeInterval last = splits.get(splits.size() - 1);
      LifetimeInterval original = lifetimes.virtualIntervals.get(first.register);
      Assert.assertEquals(
          "Splits should start with the lifetime interval of the original register",
          first.ranges.from(),
          original.ranges.from());
      Assert.assertEquals(
          "Splits should end with the lifetime interval of the original register",
          last.ranges.to(),
          original.ranges.to());
    }
  }
}
