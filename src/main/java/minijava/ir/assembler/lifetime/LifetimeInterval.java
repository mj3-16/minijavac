package minijava.ir.assembler.lifetime;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.registers.VirtualRegister;

public class LifetimeInterval {
  public final VirtualRegister register;
  private final BlockInterval[] lifetimes;
  private int fromOrdinal;
  private int toOrdinal;

  public LifetimeInterval(VirtualRegister register, int numberOfBlocks) {
    this.register = register;
    this.lifetimes = new BlockInterval[numberOfBlocks];
    fromOrdinal = numberOfBlocks - 1;
    toOrdinal = 0;
  }

  public BlockInterval getLifetimeInBlock(CodeBlock block) {
    BlockInterval lifetime = lifetimes[block.linearizedOrdinal];
    assert lifetime == null || block.equals(lifetime.block);
    return lifetime;
  }

  public CodeBlock firstBlock() {
    return lifetimes[fromOrdinal].block;
  }

  public CodeBlock lastBlock() {
    return lifetimes[toOrdinal].block;
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
    setLifetimeInBlock(block, BlockInterval.everywhere(block));
  }

  private void setLifetimeInBlock(CodeBlock block, BlockInterval lifetime) {
    assert lifetime != null : "We can never forget an interval again";
    lifetimes[block.linearizedOrdinal] = lifetime;
    fromOrdinal = Math.min(fromOrdinal, block.linearizedOrdinal);
    toOrdinal = Math.max(toOrdinal, block.linearizedOrdinal);
  }

  public void makeAliveFrom(CodeBlock block, int instructionIndex) {
    BlockInterval lifetime = getLifetimeInBlock(block);
    int from = definedBy(instructionIndex);
    if (lifetime == null) {
      int to = usedBy(instructionIndex + 1);
      BlockInterval stillbirth = new BlockInterval(block, from, to);
      // stillBirth accounts for definitions without usages (we want to keep them because of constraints)
      setLifetimeInBlock(block, stillbirth);
    } else {
      setLifetimeInBlock(block, lifetime.from(from));
    }
  }

  public void makeAliveUntil(CodeBlock block, int instructionIndex) {
    BlockInterval lifetime = getLifetimeInBlock(block);
    if (lifetime == null) {
      setLifetimeInBlock(block, BlockInterval.everywhere(block).to(usedBy(instructionIndex)));
    } else {
      setLifetimeInBlock(block, lifetime.to(Math.max(lifetime.to, usedBy(instructionIndex))));
    }
  }

  @Override
  public String toString() {
    return "LifetimeInterval{"
        + "register="
        + register
        + ", lifetimes="
        + Arrays.toString(lifetimes)
        + '}';
  }

  private static int definedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2;
  }

  private static int usedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2 - 1;
  }

  public boolean endsBefore(BlockInterval interval) {
    if (lastBlock().linearizedOrdinal < interval.block.linearizedOrdinal) {
      return true;
    }

    if (lastBlock().linearizedOrdinal > interval.block.linearizedOrdinal) {
      return false;
    }

    BlockInterval analogInterval = getLifetimeInBlock(interval.block);
    assert analogInterval != null : "The lastBlock()s interval can't be null";
    return analogInterval.to < interval.from;
  }

  public boolean coversStartOf(BlockInterval interval) {
    BlockInterval analogInterval = getLifetimeInBlock(interval.block);
    return analogInterval != null
        && analogInterval.from <= interval.from
        && analogInterval.to >= interval.from;
  }

  public BlockInterval firstIntersectionWith(LifetimeInterval other) {
    int firstPossibleOrdinal = Math.min(fromOrdinal, other.fromOrdinal);
    int lastPossibleOrdinal = Math.max(lifetimes.length, other.lifetimes.length) - 1;
    for (int i = firstPossibleOrdinal; i <= lastPossibleOrdinal; ++i) {
      BlockInterval thisInterval = lifetimes[i];
      BlockInterval otherInterval = other.lifetimes[i];
      if (thisInterval != null && otherInterval != null) {
        BlockInterval intersection = thisInterval.intersectionWith(otherInterval);
        if (intersection != null) {
          return intersection;
        }
      }
    }
    return null;
  }

  public static class BlockInterval {
    public final CodeBlock block;
    public final int from; // inclusive, starting at -2 for PhiFunctions
    public final int to; // inclusive

    public BlockInterval(CodeBlock block, int from, int to) {
      Preconditions.checkArgument(from >= 0, "ConsecutiveRange: from < 0");
      Preconditions.checkArgument(from < to, "ConsecutiveRange: from >= to");
      this.block = block;
      this.from = from;
      this.to = to;
    }

    public BlockInterval from(int from) {
      return new BlockInterval(this.block, from, this.to);
    }

    public BlockInterval to(int to) {
      return new BlockInterval(this.block, this.from, to);
    }

    public static BlockInterval everywhere(CodeBlock block) {
      int from = 0;
      int to = usedBy(block.instructions.size());
      return new BlockInterval(block, from, to);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BlockInterval that = (BlockInterval) o;
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

    public BlockInterval intersectionWith(BlockInterval other) {
      if (!block.equals(other.block)) {
        return null;
      }
      if (from > other.to || to < other.from) {
        return null;
      }
      return new BlockInterval(block, Math.max(from, other.from), Math.min(to, other.to));
    }
  }
}
