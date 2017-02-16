package minijava.backend.allocation;

import static minijava.backend.registers.AMD64Register.A;
import static minijava.backend.registers.AMD64Register.B;
import static minijava.backend.registers.AMD64Register.DI;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.notNullValue;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import minijava.backend.ExampleProgram;
import minijava.backend.VirtualRegisterSupply;
import minijava.backend.lifetime.LifetimeAnalysis;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;
import org.junit.Assert;
import org.junit.Test;

public class LinearScanRegisterAllocationTest {
  private final VirtualRegisterSupply supply = new VirtualRegisterSupply();
  private static Set<AMD64Register> ONE_REG = Sets.newHashSet(DI);
  private static Set<AMD64Register> TWO_REGS = Sets.newHashSet(A, DI);
  private static Set<AMD64Register> THREE_REGS = Sets.newHashSet(A, B, DI);

  @Test
  public void loopCountingToFiveThreeRegs_doesntSpill() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult allocationResult =
        LinearScanRegisterAllocator.allocateRegisters(lifetimes, THREE_REGS);

    Assert.assertTrue(
        "Should not have spilled any register", allocationResult.spillSlots.isEmpty());
  }

  @Test
  public void loopCountingToFiveTwoRegs_spills() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, TWO_REGS);

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

    // r0 is the constant 5, which is the argument to the Cmp
    VirtualRegister r0 = example.registers.get(0);
    assertIsSplitOnceAtItsUse(result, r0);

    // r2 is the constant 1, which is the argument to the Add instruction in the loop
    VirtualRegister r2 = example.registers.get(2);
    assertIsSplitOnceAtItsUse(result, r2);

    // All other registers shouldn't have been split.
    Assert.assertTrue("Shouldn't have split other registers", result.spillSlots.size() == 2);
  }
}
