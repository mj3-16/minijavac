package minijava.ir.assembler.lifetime;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.registers.VirtualRegister;

public class LifetimeInterval {
  public final VirtualRegister register;
  public final Map<CodeBlock, ConsecutiveRange> lifetimes = new HashMap<>();

  public LifetimeInterval(VirtualRegister register) {
    this.register = register;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LifetimeInterval that = (LifetimeInterval) o;
    return Objects.equals(register, that.register) && Objects.equals(lifetimes, that.lifetimes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(register, lifetimes);
  }

  public void makeAliveInWholeBlock(CodeBlock block) {
    lifetimes.put(block, ConsecutiveRange.ofBlock(block));
  }

  public void makeAliveFrom(CodeBlock block, int instructionIndex) {
    if (lifetimes.containsKey(block)) {
      ConsecutiveRange old = lifetimes.get(block);
      lifetimes.put(block, old.from(definedBy(instructionIndex)));
    } else {
      ConsecutiveRange stillbirth =
          new ConsecutiveRange(definedBy(instructionIndex), usedBy(instructionIndex + 1));
      // stillBirth accounts for definitions without usages (we want to keep them because of constraints)
      lifetimes.put(block, stillbirth);
    }
  }

  public void makeAliveUntil(CodeBlock block, int instructionIndex) {
    lifetimes.compute(
        block,
        (k, range) -> {
          if (range == null) {
            ConsecutiveRange wholeBlock = ConsecutiveRange.ofBlock(block);
            return wholeBlock.to(usedBy(instructionIndex));
          }
          int newTo = Math.max(range.to, usedBy(instructionIndex));
          return range.to(newTo);
        });
  }

  @Override
  public String toString() {
    return "LifetimeInterval{" + "register=" + register + ", lifetimes=" + lifetimes + '}';
  }

  private static int definedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2;
  }

  private static int usedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2 - 1;
  }

  public static class ConsecutiveRange {
    public final int from; // inclusive, starting at -2 for PhiFunctions
    public final int to; // inclusive

    public ConsecutiveRange(int from, int to) {
      Preconditions.checkArgument(from < to, "ConsecutiveRange: from >= to");
      this.from = from;
      this.to = to;
    }

    public ConsecutiveRange from(int from) {
      return new ConsecutiveRange(from, this.to);
    }

    public ConsecutiveRange to(int to) {
      return new ConsecutiveRange(this.from, to);
    }

    public static ConsecutiveRange ofBlock(CodeBlock block) {
      int from = definedBy(-1); // Defined before the Phi
      int to = usedBy(block.instructions.size()); // Still alive after the last instruction
      return new ConsecutiveRange(from, to);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ConsecutiveRange that = (ConsecutiveRange) o;
      return from == that.from && to == that.to;
    }

    @Override
    public int hashCode() {
      return Objects.hash(from, to);
    }

    @Override
    public String toString() {
      return String.format("[%d, %d]", from, to);
    }
  }
}
