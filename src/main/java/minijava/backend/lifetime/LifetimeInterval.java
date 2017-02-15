package minijava.backend.lifetime;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.*;
import minijava.backend.block.CodeBlock;
import minijava.backend.registers.Register;
import minijava.backend.registers.VirtualRegister;
import org.jetbrains.annotations.Nullable;

public class LifetimeInterval {

  public static final Comparator<LifetimeInterval> COMPARING_DEF =
      Comparator.comparing(LifetimeInterval::from).thenComparingInt(li -> li.register.id);
  public final VirtualRegister register;
  public final NavigableSet<BlockPosition> defAndUses;
  public final LinearLiveRanges ranges;
  public Set<Register> fromHints = new HashSet<>();
  public Set<Register> toHints = new HashSet<>();

  public LifetimeInterval(VirtualRegister register) {
    this(register, new TreeSet<>(), new LinearLiveRanges());
  }

  private LifetimeInterval(
      VirtualRegister register, NavigableSet<BlockPosition> defAndUses, LinearLiveRanges ranges) {
    this.register = register;
    this.defAndUses = defAndUses;
    this.ranges = ranges;
  }

  @Nullable
  public BlockPosition firstDefOrUse() {
    return defAndUses.isEmpty() ? null : defAndUses.first();
  }

  @Nullable
  public BlockPosition lastDefOrUse() {
    return defAndUses.isEmpty() ? null : defAndUses.last();
  }

  @Nullable
  public BlockPosition nextUseAfter(BlockPosition start) {
    Iterator<BlockPosition> it = defAndUses.tailSet(start).iterator();
    return it.hasNext() ? it.next() : null;
  }

  @Nullable
  public LiveRange getLifetimeInBlock(CodeBlock block) {
    List<LiveRange> ranges = this.ranges.getLiveRanges(block);
    assert ranges.size() <= 1
        : "The lifetime interval of a virtual register should only have one live range per block";
    return ranges.size() == 0 ? null : ranges.get(0);
  }

  public BlockPosition from() {
    return ranges.from();
  }

  public BlockPosition to() {
    return ranges.to();
  }

  public CodeBlock firstBlock() {
    return ranges.from().block;
  }

  public CodeBlock lastBlock() {
    return ranges.to().block;
  }

  public void makeAliveInWholeBlock(CodeBlock block) {
    setLiveRange(LiveRange.everywhere(block));
  }

  private void setLiveRange(LiveRange range) {
    assert range != null : "We can never forget a live range again";
    ranges.deleteLiveRanges(range.block);
    ranges.addLiveRange(range);
  }

  public void setDef(BlockPosition position) {
    assert position.isDef() : "Was not a definition";
    LiveRange lifetime = getLifetimeInBlock(position.block);
    defAndUses.add(position);
    assert lifetime != null : "There should be no defs without a later use.";
    setLiveRange(lifetime.from(position.pos));
  }

  public void addUse(BlockPosition position) {
    assert position.isUse() : "Was not a use";
    defAndUses.add(position);
    LiveRange lifetime = getLifetimeInBlock(position.block);
    if (lifetime == null) {
      setLiveRange(new LiveRange(position.block, 0, position.pos));
    }
  }

  public boolean endsBefore(BlockPosition position) {
    if (lastBlock().linearizedOrdinal < position.block.linearizedOrdinal) {
      return true;
    }

    if (lastBlock().linearizedOrdinal > position.block.linearizedOrdinal) {
      return false;
    }

    LiveRange analogInterval = getLifetimeInBlock(position.block);
    assert analogInterval != null : "The lastBlock()s interval can't be null";
    return analogInterval.to < position.pos;
  }

  public boolean covers(BlockPosition position) {
    LiveRange analogInterval = getLifetimeInBlock(position.block);
    return analogInterval != null && analogInterval.contains(position);
  }

  public Split<LifetimeInterval> splitBefore(BlockPosition pos) {
    checkArgument(ranges.from().compareTo(pos) <= 0, "pos must lie after the interval's def");
    checkArgument(ranges.to().compareTo(pos) >= 0, "pos must be before the interval dies");
    // Note that the after split interval has a use as its first defAndUses... This might bring
    // confusion later on, but there is no sensible def index to choose.
    Split<LinearLiveRanges> splitRanges = ranges.splitBefore(pos);
    LifetimeInterval before =
        new LifetimeInterval(register, defAndUses.headSet(pos, false), splitRanges.before);
    LifetimeInterval after =
        new LifetimeInterval(register, defAndUses.tailSet(pos, true), splitRanges.after);
    before.fromHints = fromHints;
    after.toHints = toHints;

    // We can also be more precise for the begin and end of the split interval.
    // Note that the lifetime may stretch beyond the last use! (e.g. loops)
    // That's why we only modify the from part for the after split.
    // This also destroys SSA form of the intervals: If we split within a loop, both the before and
    // after definitions are reaching.
    System.out.println("before = " + before);
    System.out.println("after = " + after);
    return new Split<>(before, after);
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
    return "LifetimeInterval{"
        + "register="
        + register
        + ", defAndUses="
        + defAndUses
        + ", ranges="
        + ranges
        + ", fromHints="
        + fromHints
        + ", toHints="
        + toHints
        + '}';
  }
}
