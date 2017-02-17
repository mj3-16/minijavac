package minijava.backend.allocation;

import static minijava.backend.registers.AMD64Register.A;
import static minijava.backend.registers.AMD64Register.B;
import static minijava.backend.registers.AMD64Register.DI;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.notNullValue;
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

    Assert.assertTrue("Should not have spilled any register", result.spillSlots.isEmpty());
  }

  @Test
  public void loopCountingToFiveTwoRegs_spills() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, TWO_REGS);

    assertLifetimesMatch(lifetimes, result);

    // r0 is the constant 5, which is the argument to the Cmp
    VirtualRegister r0 = example.registers.get(0);
    assertIsSplitOnceAtItsUse(result, r0);

    // r2 is the constant 1, which is the argument to the Add instruction in the loop
    VirtualRegister r2 = example.registers.get(2);
    assertIsSplitOnceAtItsUse(result, r2);

    // All other registers shouldn't have been split.
    Assert.assertTrue("Shouldn't have split other registers", result.spillSlots.size() == 2);
  }

  private void assertIsSplitOnceAtItsUse(AllocationResult allocationResult, VirtualRegister reg) {
    List<LifetimeInterval> splits = allocationResult.splitLifetimes.get(reg);
    Integer spillSlot0 = allocationResult.spillSlots.get(reg);

    Assert.assertThat("Has split the interval for " + reg, spillSlot0, notNullValue());
    Assert.assertThat(
        "Has split the interval for " + reg + " exactly one", splits, is(iterableWithSize(2)));
    LifetimeInterval after = splits.get(1);
    Assert.assertEquals(
        "Has split the interval for " + reg + " at its use", after.uses.firstKey(), after.from());
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
