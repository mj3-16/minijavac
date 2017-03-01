package minijava.backend.allocation;

import static minijava.backend.CodeBlockBuilder.asLinearization;
import static minijava.backend.CodeBlockBuilder.newBlock;
import static minijava.backend.operands.OperandUtils.imm;
import static minijava.backend.operands.OperandUtils.reg;
import static minijava.backend.registers.AMD64Register.A;
import static minijava.backend.registers.AMD64Register.D;
import static minijava.backend.registers.AMD64Register.DI;
import static minijava.backend.registers.AMD64Register.SI;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.jooq.lambda.Seq.seq;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.backend.ExampleProgram;
import minijava.backend.VirtualRegisterSupply;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.CodeBlock.ExitArity.Zero;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Mov;
import minijava.backend.lifetime.BlockPosition;
import minijava.backend.lifetime.LifetimeAnalysis;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.VirtualRegister;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public class LinearScanRegisterAllocationTest {
  private static Set<AMD64Register> ONE_REG = Sets.newHashSet(DI);
  private static Set<AMD64Register> TWO_REGS = Sets.newHashSet(DI, SI);
  private static Set<AMD64Register> THREE_REGS = Sets.newHashSet(A, DI, SI);
  private static Set<AMD64Register> FOUR_REGS = Sets.newHashSet(A, D, DI, SI);
  private static Set<AMD64Register> ALL = AMD64Register.ALLOCATABLE;

  private static void assertNumberOfSpills(AllocationResult result, int spills) {
    assertThat(
        "Shouldn't have spilled more than " + spills + " registers",
        result.spillSlots.size(),
        is(lessThanOrEqualTo(spills)));
  }

  private static void assertSpillSlotsDontOverlap(
      LifetimeAnalysisResult lifetimes, AllocationResult result) {
    for (int slot : new HashSet<>(result.spillSlots.values())) {
      List<LifetimeInterval> sameSlot =
          seq(result.spillSlots)
              .filter(t -> t.v2 == slot)
              .map(t -> lifetimes.virtualIntervals.get(t.v1))
              .toList();
      sameSlot.sort(LifetimeInterval.COMPARING_DEF);
      // now assert that no consecutive intervals with the same spill slot overlap
      for (int i = 0; i < sameSlot.size() - 1; ++i) {
        LifetimeInterval cur = sameSlot.get(i);
        LifetimeInterval next = sameSlot.get(i + 1);
        assertThat(
            "Lifetimes of registers with the same spill slot may not overlap",
            cur.ranges.firstIntersectionWith(next.ranges),
            nullValue());
      }
    }
  }

  private static void assertLifetimesMatch(
      LifetimeAnalysisResult lifetimes, AllocationResult result) {
    for (List<LifetimeInterval> splits : result.splitLifetimes.values()) {
      for (int i = 0; i < splits.size() - 1; ++i) {
        LifetimeInterval prev = splits.get(i);
        LifetimeInterval next = splits.get(i + 1);
        assertThat(
            "Splits of the same register may not overlap",
            prev.ranges.firstIntersectionWith(next.ranges),
            nullValue());
        BlockPosition endPrev = prev.ranges.to();
        BlockPosition startNext = next.ranges.from();
        assertTrue(
            "Splits of the same register may not have holes",
            !endPrev.block.equals(startNext.block) || endPrev.pos + 1 == startNext.pos);

        Assert.assertTrue(
            "Consecutive splits which are spilled should be merged",
            result.allocation.get(prev) != null || result.allocation.get(next) != null);

        Assert.assertTrue(
            "Consecutive splits should not both have a register assigned",
            result.allocation.get(prev) == null || result.allocation.get(next) == null);
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

  public static class RegressionTests {
    @Test
    public void laterUsedInterval_shouldntReassignSpillSlot() {
      VirtualRegisterSupply supply = new VirtualRegisterSupply();
      VirtualRegister r0 = supply.next();
      VirtualRegister r1 = supply.next();

      CodeBlock block =
          newBlock("block")
              .addInstruction(new Mov(imm(5), reg(r0)))
              .addInstruction(new Call("moop", Lists.newArrayList())) // spills r0
              .addInstruction(new Mov(imm(0), reg(r1)))
              .addInstruction(new Mov(reg(r0), reg(DI))) // reloads r0
              .addInstruction(new Call("moop", Lists.newArrayList(reg(DI)))) // spills r1
              .addInstruction(new Mov(reg(r1), reg(DI))) // reloads r1
              .addInstruction(new Call("moop", Lists.newArrayList(reg(DI))))
              .build();
      block.exit = new Zero();

      List<CodeBlock> program = asLinearization(block);

      program.forEach(CodeBlock::printDebugInfo);
      LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(program);
      AllocationResult result = LinearScanRegisterAllocator.allocateRegisters(lifetimes, ONE_REG);

      // default checks
      assertLifetimesMatch(lifetimes, result);
      assertSpillSlotsDontOverlap(lifetimes, result);
      assertNumberOfSpills(result, 2);
    }
  }

  @RunWith(Parameterized.class)
  public static class ParameterizedTests {
    private final ExampleProgram example;
    private final Set<AMD64Register> allocatable;
    private final int maxSpills;

    public ParameterizedTests(
        String name, ExampleProgram example, Set<AMD64Register> allocatable, int maxSpills) {
      this.example = example;
      this.allocatable = allocatable;
      this.maxSpills = maxSpills;
    }

    @Parameters(name = "{index}: {0}, max spill {3}")
    public static Collection<Object[]> data() {
      ExampleProgram countToFive = ExampleProgram.loopCountingToFive();
      ExampleProgram doubleFib = ExampleProgram.doubleFib();
      return Arrays.asList(
          new Object[][] {
            {"countToFive(1)", countToFive, ONE_REG, 4},
            {"countToFive(2)", countToFive, TWO_REGS, 2},
            {"countToFive(3)", countToFive, THREE_REGS, 0},
            {"countToFive(all)", countToFive, ALL, 0},
            {"doubleFib(1)", doubleFib, ONE_REG, 9},
            {"doubleFib(2)", doubleFib, TWO_REGS, 6},
            {"doubleFib(3)", doubleFib, THREE_REGS, 3},
            {"doubleFib(4)", doubleFib, FOUR_REGS, 2},
            {"doubleFib(all)", doubleFib, ALL, 1}, // One spill still because of a call
          });
    }

    @Test
    public void test() {
      example.program.forEach(CodeBlock::printDebugInfo);
      LifetimeAnalysisResult lifetimes = LifetimeAnalysis.analyse(example.program);
      AllocationResult result =
          LinearScanRegisterAllocator.allocateRegisters(lifetimes, allocatable);

      //result.printDebugInfo();

      assertLifetimesMatch(lifetimes, result);
      assertSpillSlotsDontOverlap(lifetimes, result);
      assertNumberOfSpills(result, maxSpills);
    }
  }
}
