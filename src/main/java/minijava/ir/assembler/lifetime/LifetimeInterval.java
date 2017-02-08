package minijava.ir.assembler.lifetime;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jooq.lambda.tuple.Tuple.tuple;

import java.util.Arrays;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jooq.lambda.tuple.Tuple2;

public class LifetimeInterval {
  public final VirtualRegister register;
  public final SortedSet<BlockPosition> defAndUses;
  private final BlockInterval[] lifetimes;

  public LifetimeInterval(VirtualRegister register, int numberOfBlocks) {
    this(register, numberOfBlocks, new TreeSet<>());
  }

  private LifetimeInterval(
      VirtualRegister register, int numberOfBlocks, SortedSet<BlockPosition> defAndUses) {
    this.register = register;
    this.lifetimes = new BlockInterval[numberOfBlocks];
    this.defAndUses = defAndUses;
  }

  public BlockInterval getLifetimeInBlock(CodeBlock block) {
    BlockInterval lifetime = lifetimes[block.linearizedOrdinal];
    assert lifetime == null || block.equals(lifetime.block);
    return lifetime;
  }

  public CodeBlock firstBlock() {
    return defAndUses.first().block;
  }

  public CodeBlock lastBlock() {
    return defAndUses.last().block;
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
    return Objects.equals(register, that.register)
        && Objects.equals(defAndUses, that.defAndUses)
        && Arrays.equals(lifetimes, that.lifetimes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(register, defAndUses, lifetimes);
  }

  public void makeAliveInWholeBlock(CodeBlock block) {
    setLifetimeInBlock(block, everywhere(block));
  }

  private static BlockInterval everywhere(CodeBlock block) {
    int from = 0;
    int to = usedBy(block.instructions.size());
    return new BlockInterval(block, from, to);
  }

  private void setLifetimeInBlock(CodeBlock block, BlockInterval lifetime) {
    assert lifetime != null : "We can never forget an interval again";
    lifetimes[block.linearizedOrdinal] = lifetime;
  }

  public void setDef(CodeBlock block, int instructionIndex) {
    BlockInterval lifetime = getLifetimeInBlock(block);
    int def = definedBy(instructionIndex);
    defAndUses.add(new BlockPosition(block, def));
    if (lifetime == null) {
      // this accounts for definitions without usages (we want to keep them because of constraints)
      int pseudoUse = usedBy(instructionIndex + 1);
      defAndUses.add(new BlockPosition(block, pseudoUse));
      BlockInterval stillbirth = new BlockInterval(block, def, pseudoUse);
      setLifetimeInBlock(block, stillbirth);
    } else {
      setLifetimeInBlock(block, lifetime.from(def));
    }
  }

  public void addUse(CodeBlock block, int instructionIndex) {
    int usage = usedBy(instructionIndex);
    defAndUses.add(new BlockPosition(block, usage));
    BlockInterval lifetime = getLifetimeInBlock(block);
    if (lifetime == null) {
      setLifetimeInBlock(block, everywhere(block).to(usage));
    } else {
      setLifetimeInBlock(block, lifetime.to(Math.max(lifetime.to, usage)));
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

  private int firstBlockOrdinal() {
    return defAndUses.first().block.linearizedOrdinal;
  }

  private int lastBlockOrdinal() {
    return defAndUses.last().block.linearizedOrdinal;
  }

  public BlockInterval firstIntersectionWith(LifetimeInterval other) {
    int firstPossibleOrdinal = Math.max(firstBlockOrdinal(), other.firstBlockOrdinal());
    int lastPossibleOrdinal = Math.min(lastBlockOrdinal(), other.lastBlockOrdinal());
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

  public Tuple2<LifetimeInterval, LifetimeInterval> splitBefore(BlockPosition pos) {
    checkArgument(defAndUses.first().compareTo(pos) < 0, "pos must lie after the interval's def");
    checkArgument(defAndUses.last().compareTo(pos) > 0, "pos must lie before the last use");
    // Not that the after split interval has a use as its first defAndUses... This might bring
    // confusion later on, but there is no sensible def index to choose.
    return tuple(partBeforeOrAfter(pos, false), partBeforeOrAfter(pos, true));
  }

  private LifetimeInterval partBeforeOrAfter(BlockPosition splitPos, boolean after) {
    SortedSet<BlockPosition> defAndUses =
        after ? this.defAndUses.tailSet(splitPos) : this.defAndUses.headSet(splitPos);
    BlockPosition firstPos = defAndUses.first();
    BlockPosition lastPos = defAndUses.last();
    LifetimeInterval split = new LifetimeInterval(register, lifetimes.length, defAndUses);
    // We need to copy lifetime intervals between the the first and last use.
    int first = firstPos.block.linearizedOrdinal;
    int last = lastPos.block.linearizedOrdinal;
    System.arraycopy(lifetimes, first, split.lifetimes, first, last + 1 - first);

    // We can also be more precise for the begin and end of the split interval.
    // Note that the lifetime may stretch beyond the last use! (e.g. loops)
    // That's why we only modify the from part when we are producing the after split.
    // This also destroys SSA form of the intervals.
    if (after) {
      split.lifetimes[first] = split.lifetimes[first].from(firstPos.useDefIndex);
    } else {
      assert firstPos.useDefIndex == split.lifetimes[first].from;
      split.lifetimes[last] = split.lifetimes[last].to(lastPos.useDefIndex);
    }

    return split;
  }
}
