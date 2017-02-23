package minijava.backend.allocation;

import static minijava.backend.registers.AMD64Register.A;
import static minijava.backend.registers.AMD64Register.B;
import static minijava.backend.registers.AMD64Register.DI;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import minijava.backend.ExampleProgram;
import minijava.backend.block.CodeBlock;
import minijava.backend.lifetime.BlockPosition;
import minijava.backend.lifetime.LifetimeAnalysis;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.registers.AMD64Register;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LinearScanRegisterAllocationTest {
  private static Set<AMD64Register> ONE_REG = Sets.newHashSet(DI);
  private static Set<AMD64Register> TWO_REGS = Sets.newHashSet(A, DI);
  private static Set<AMD64Register> THREE_REGS = Sets.newHashSet(A, B, DI);
  private final ExampleProgram example;
  private final Set<AMD64Register> allocatable;
  private final int maxSpills;

  @Parameters(name = "{index}: {0}, max spill {3}")
  public static Collection<Object[]> data() {
    ExampleProgram countToFive = ExampleProgram.loopCountingToFive();
    ExampleProgram doubleFib = ExampleProgram.doubleFib();
    return Arrays.asList(
        new Object[][] {
          {"countToFive(1)", countToFive, ONE_REG, 4},
          {"countToFive(2)", countToFive, TWO_REGS, 2},
          {"countToFive(3)", countToFive, THREE_REGS, 0},
          {"doubleFib(1)", doubleFib, ONE_REG, 9},
          {"doubleFib(2)", doubleFib, TWO_REGS, 6},
          //{ "doubleFib(3)", doubleFib, THREE_REGS, 0 },
        });
  }

  public LinearScanRegisterAllocationTest(
      String name, ExampleProgram example, Set<AMD64Register> allocatable, int maxSpills) {
    this.example = example;
    this.allocatable = allocatable;
    this.maxSpills = maxSpills;
  }

  @Test
  public void test() {
    example.program.forEach(CodeBlock::printDebugInfo);
    LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
    AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, allocatable);

    //result.printDebugInfo();

    assertLifetimesMatch(lifetimes, result);

    assertNumberOfSpills(result, maxSpills);
  }

  private void assertNumberOfSpills(AllocationResult result, int spills) {
    Assert.assertThat(
        "Shouldn't have split more than " + spills + " registers",
        result.spillSlots.size(),
        is(lessThanOrEqualTo(spills)));
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
