package minijava.ir.assembler.lifetime;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.registers.VirtualRegister;

public class LifetimeInterval {

  public static final Comparator<LifetimeInterval> COMPARING_DEF =
      Comparator.comparing(LifetimeInterval::definition);
  public final VirtualRegister register;
  public final SortedSet<BlockPosition> defAndUses;
  private final LinearLiveRanges ranges;

  public LifetimeInterval(VirtualRegister register) {
    this(register, new TreeSet<>(), new LinearLiveRanges());
  }

  private LifetimeInterval(
      VirtualRegister register, SortedSet<BlockPosition> defAndUses, LinearLiveRanges ranges) {
    this.register = register;
    this.defAndUses = defAndUses;
    this.ranges = ranges;
  }

  public LiveRange getLifetimeInBlock(CodeBlock block) {
    List<LiveRange> ranges = this.ranges.getLiveRanges(block);
    assert ranges.size() <= 1
        : "The lifetime interval of a virtual register should only have one live range per block";
    return ranges.size() == 0 ? null : ranges.get(0);
  }

  public BlockPosition definition() {
    // In the case that the interval has been split, defAndUses.first() is a use! We simulate a def
    // by decrementing its pos by one.
    BlockPosition first = defAndUses.first();
    boolean isDef = first.pos % 2 == 0;
    if (!isDef) {
      return new BlockPosition(first.block, first.pos - 1);
    }
    return first;
  }

  public CodeBlock firstBlock() {
    return defAndUses.first().block;
  }

  public CodeBlock lastBlock() {
    return defAndUses.last().block;
  }

  public void makeAliveInWholeBlock(CodeBlock block) {
    setLiveRange(everywhere(block));
  }

  private static LiveRange everywhere(CodeBlock block) {
    int from = 0;
    int to = usedBy(block.instructions.size());
    return new LiveRange(block, from, to);
  }

  private void setLiveRange(LiveRange range) {
    assert range != null : "We can never forget a live range again";
    ranges.replaceLiveRange(range);
  }

  public void setDef(CodeBlock block, int instructionIndex) {
    LiveRange lifetime = getLifetimeInBlock(block);
    int def = definedBy(instructionIndex);
    defAndUses.add(new BlockPosition(block, def));
    assert lifetime != null : "There should be no defs without a later use.";
    setLiveRange(lifetime.from(def));
  }

  public void addUse(CodeBlock block, int instructionIndex) {
    int usage = usedBy(instructionIndex);
    defAndUses.add(new BlockPosition(block, usage));
    LiveRange lifetime = getLifetimeInBlock(block);
    if (lifetime == null) {
      setLiveRange(new LiveRange(block, 0, usage));
    } else {
      setLiveRange(lifetime.to(Math.max(lifetime.to, usage)));
    }
  }

  private static int definedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2;
  }

  private static int usedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2 - 1;
  }

  public boolean endsBefore(LiveRange interval) {
    if (lastBlock().linearizedOrdinal < interval.block.linearizedOrdinal) {
      return true;
    }

    if (lastBlock().linearizedOrdinal > interval.block.linearizedOrdinal) {
      return false;
    }

    LiveRange analogInterval = getLifetimeInBlock(interval.block);
    assert analogInterval != null : "The lastBlock()s interval can't be null";
    return analogInterval.to < interval.from;
  }

  public boolean coversStartOf(LiveRange interval) {
    LiveRange analogInterval = getLifetimeInBlock(interval.block);
    return analogInterval != null
        && analogInterval.from <= interval.from
        && analogInterval.to >= interval.from;
  }

  public BlockPosition firstIntersectionWith(LifetimeInterval other) {
    return ranges.firstIntersectionWith(other.ranges);
  }

  public Split<LifetimeInterval> splitBefore(BlockPosition pos) {
    checkArgument(defAndUses.first().compareTo(pos) < 0, "pos must lie after the interval's def");
    checkArgument(defAndUses.last().compareTo(pos) > 0, "pos must lie before the last use");
    // Note that the after split interval has a use as its first defAndUses... This might bring
    // confusion later on, but there is no sensible def index to choose.
    Split<LinearLiveRanges> splitRanges = ranges.splitBefore(pos);
    LifetimeInterval before =
        new LifetimeInterval(register, defAndUses.headSet(pos), splitRanges.before);
    LifetimeInterval after =
        new LifetimeInterval(register, defAndUses.tailSet(pos), splitRanges.after);

    // We can also be more precise for the begin and end of the split interval.
    // Note that the lifetime may stretch beyond the last use! (e.g. loops)
    // That's why we only modify the from part for the after split.
    // This also destroys SSA form of the intervals: If we split within a loop, both the before and
    // after definitions are reaching.
    shortenFromRange(before);
    shortenToRange(before);
    shortenFromRange(after);
    return new Split<>(before, after);
  }

  private static void shortenFromRange(LifetimeInterval li) {
    BlockPosition first = li.defAndUses.first();
    LiveRange range = li.getLifetimeInBlock(first.block);
    assert range != null; // It's the block with the first usage after all
    li.ranges.replaceLiveRange(range.from(first.pos));
  }

  private void shortenToRange(LifetimeInterval li) {
    BlockPosition last = li.defAndUses.last();
    LiveRange range = li.getLifetimeInBlock(last.block);
    assert range != null; // It's the block with the last usage after all
    li.ranges.replaceLiveRange(range.to(last.pos));
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
        && Objects.equals(ranges, that.ranges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(register, defAndUses, ranges);
  }

  @Override
  public String toString() {
    return "LifetimeInterval{" + "register=" + register + ", ranges=" + ranges + '}';
  }
}
