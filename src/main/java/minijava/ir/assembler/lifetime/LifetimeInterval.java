package minijava.ir.assembler.lifetime;

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
    int start = instructionIndex * 2;
    ConsecutiveRange stillbirth = new ConsecutiveRange(start, start + 1);
    lifetimes.getOrDefault(block, stillbirth).from = start;
  }

  public void makeAliveUntil(CodeBlock block, int instructionIndex) {
    int end = instructionIndex * 2 - 1;
    ConsecutiveRange wholeBlock = ConsecutiveRange.ofBlock(block);
    lifetimes.getOrDefault(block, wholeBlock).to = end;
  }

  @Override
  public String toString() {
    return "LifetimeInterval{" + "register=" + register + ", lifetimes=" + lifetimes + '}';
  }

  public static class ConsecutiveRange {
    public int from; // inclusive, starting at -2 for PhiFunctions
    public int to; // inclusive

    public ConsecutiveRange(int from, int to) {
      this.from = from;
      this.to = to;
    }

    public static ConsecutiveRange ofBlock(CodeBlock block) {
      int start = -2; // Defined before the Phi
      int end = block.instructions.size() * 2; // Still alive after the last instruction
      return new ConsecutiveRange(start, end);
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
