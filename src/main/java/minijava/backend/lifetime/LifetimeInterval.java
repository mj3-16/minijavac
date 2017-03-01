package minijava.backend.lifetime;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static org.jooq.lambda.Seq.seq;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import minijava.backend.block.CodeBlock;
import minijava.backend.operands.Use;
import minijava.backend.registers.Register;
import minijava.backend.registers.VirtualRegister;
import org.jetbrains.annotations.Nullable;

public class LifetimeInterval {

  public static final Comparator<LifetimeInterval> COMPARING_DEF =
      Comparator.comparing(LifetimeInterval::from)
          .thenComparing(LifetimeInterval::firstUse, nullsLast(naturalOrder()))
          .thenComparingInt(li -> li.register.id);
  public final VirtualRegister register;
  public final NavigableMap<BlockPosition, UseSite> uses;
  public final LinearLiveRanges ranges;
  public Set<Register> fromHints = new HashSet<>();
  public Set<Register> toHints = new HashSet<>();

  public LifetimeInterval(VirtualRegister register) {
    this(register, new TreeMap<>(), new LinearLiveRanges());
  }

  private LifetimeInterval(
      VirtualRegister register,
      NavigableMap<BlockPosition, UseSite> uses,
      LinearLiveRanges ranges) {
    this.register = register;
    this.uses = uses;
    this.ranges = ranges;
  }

  @Nullable
  public BlockPosition firstUse() {
    return uses.isEmpty() ? null : uses.firstKey();
  }

  @Nullable
  public BlockPosition lastUse() {
    return uses.isEmpty() ? null : uses.lastKey();
  }

  @Nullable
  public BlockPosition lastDef() {
    return seq(uses.navigableKeySet().descendingSet())
        .filter(BlockPosition::isDef)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  public BlockPosition nextUseAfter(BlockPosition pos) {
    Iterator<BlockPosition> it = uses.tailMap(pos).keySet().iterator();
    return it.hasNext() ? it.next() : null;
  }

  @Nullable
  public BlockPosition firstUseNeedingARegister() {
    for (UseSite use : uses.values()) {
      if (!use.mayBeReplacedByMemoryAccess) {
        return use.position;
      }
    }
    return null;
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
    BlockPosition from = ranges.from();
    assert from != null : "A lifetime interval may never be empty";
    return from.block;
  }

  public CodeBlock lastBlock() {
    BlockPosition to = ranges.to();
    assert to != null : "A lifetime interval may never be empty";
    return to.block;
  }

  public void makeAliveInWholeBlock(CodeBlock block) {
    setLiveRange(LiveRange.everywhere(block));
  }

  private void setLiveRange(LiveRange range) {
    assert range != null : "We can never forget a live range again";
    ranges.deleteLiveRanges(range.block);
    ranges.addLiveRange(range);
  }

  public void setDef(BlockPosition position, Use def) {
    assert position.isDef() : "Was not a definition";
    LiveRange lifetime = getLifetimeInBlock(position.block);
    uses.put(position, new UseSite(position, def.mayBeReplacedByMemoryAccess));
    System.out.println(this);
    assert lifetime != null : "There should be no defs without a later use.";
    setLiveRange(lifetime.from(position.pos));
  }

  public void addUse(BlockPosition position, Use use) {
    assert position.isUse() : "Was not a use";
    uses.put(position, new UseSite(position, use.mayBeReplacedByMemoryAccess));
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
    checkArgument(ranges.from().compareTo(pos) < 0, "pos must lie after the interval's def");
    checkArgument(ranges.to().compareTo(pos) >= 0, "pos must be before the interval dies");
    // Note that the after split interval has a use as its first uses... This might bring
    // confusion later on, but there is no sensible def index to choose.
    Split<LinearLiveRanges> splitRanges = ranges.splitBefore(pos);
    LifetimeInterval before =
        new LifetimeInterval(register, uses.headMap(pos, false), splitRanges.before);
    LifetimeInterval after =
        new LifetimeInterval(register, uses.tailMap(pos, true), splitRanges.after);
    before.fromHints = fromHints;
    after.toHints = toHints;

    // We can also be more precise for the begin and end of the split interval.
    // Note that the lifetime may stretch beyond the last use! (e.g. loops)
    // That's why we only modify the from part for the after split.
    // This also destroys SSA form of the intervals: If we split within a loop, both the before and
    // after definitions are reaching.
    return new Split<>(before, after);
  }

  /** Assumes that the intervals don't overlap. */
  public static LifetimeInterval coalesceIntervals(List<LifetimeInterval> toMerge) {
    assert seq(toMerge).map(li -> li.register).distinct().count() == 1
        : "Can only coalesce intervals of the same register";
    if (toMerge.size() == 1) {
      return toMerge.get(0);
    }
    VirtualRegister register = toMerge.get(0).register;
    TreeMap<BlockPosition, UseSite> uses = new TreeMap<>();
    LinearLiveRanges ranges = new LinearLiveRanges();
    seq(toMerge).map(li -> li.uses).forEach(uses::putAll);
    seq(toMerge).map(li -> li.ranges).forEach(ranges::addAllRanges);
    return new LifetimeInterval(register, uses, ranges);
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
        && Objects.equals(uses, that.uses)
        && Objects.equals(ranges, that.ranges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(register, uses, ranges);
  }

  @Override
  public String toString() {
    return "LifetimeInterval{"
        + "register="
        + register
        + ", uses="
        + uses.values()
        + ", ranges="
        + ranges
        + ", fromHints="
        + fromHints
        + ", toHints="
        + toHints
        + '}';
  }
}
