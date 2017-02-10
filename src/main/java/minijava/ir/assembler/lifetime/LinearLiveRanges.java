package minijava.ir.assembler.lifetime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import minijava.ir.assembler.block.CodeBlock;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;

public class LinearLiveRanges {

  /** Maps from BlockPositions to end indices in the same block. */
  private final TreeMap<BlockPosition, Integer> ranges;

  public LinearLiveRanges() {
    this(new TreeMap<>());
  }

  private LinearLiveRanges(SortedMap<BlockPosition, Integer> ranges) {
    this.ranges = new TreeMap<>(ranges);
  }

  public List<LiveRange> getLiveRanges(CodeBlock block) {
    BlockPosition from = new BlockPosition(block, 0);
    BlockPosition to = new BlockPosition(block, Integer.MAX_VALUE);
    List<LiveRange> ret = new ArrayList<>();
    for (Entry<BlockPosition, Integer> entry : ranges.subMap(from, to).entrySet()) {
      ret.add(new LiveRange(block, entry.getKey().pos, entry.getValue()));
    }
    return ret;
  }

  @Nullable
  public LiveRange getLiveRangeContaining(BlockPosition position) {
    LiveRange range = toRange(ranges.headMap(position, true).lastEntry());
    if (range.contains(position)) {
      return range;
    }
    return null;
  }

  public void addLiveRange(LiveRange range) {
    BlockPosition from = new BlockPosition(range.block, range.from);
    BlockPosition to = new BlockPosition(range.block, range.to);
    assert ranges.subMap(from, to).isEmpty() : "Tried to add intersecting live range";
    ranges.put(from, range.to);
  }

  public void deleteLiveRangeContaining(BlockPosition pos) {
    LiveRange range = toRange(ranges.headMap(pos, true).lastEntry());
    if (range.contains(pos)) {
      ranges.remove(range.fromPosition());
    }
  }

  public void deleteLiveRanges(CodeBlock block) {
    BlockPosition from = new BlockPosition(block, 0);
    BlockPosition to = new BlockPosition(block, Integer.MAX_VALUE);
    ranges.subMap(from, to).clear();
  }

  public Split<LinearLiveRanges> splitBefore(BlockPosition beforePos) {
    LinearLiveRanges before = new LinearLiveRanges(ranges.headMap(beforePos));
    LinearLiveRanges after = new LinearLiveRanges(ranges.tailMap(beforePos));
    return new Split<>(before, after);
  }

  public BlockPosition firstIntersectionWith(LinearLiveRanges other) {
    BlockPosition first = Seq.of(firstFrom(), other.firstFrom()).max().get();
    BlockPosition last = Seq.of(lastTo(), other.lastTo()).min().get();

    Iterator<Entry<BlockPosition, Integer>> itA = iterateEntriesInRange(first, last);
    Iterator<Entry<BlockPosition, Integer>> itB = other.iterateEntriesInRange(first, last);
    if (itA.hasNext() && itB.hasNext()) {
      LiveRange a = toRange(itA.next());
      LiveRange b = toRange(itB.next());
      while (true) {
        LiveRange intersection = a.intersectionWith(b);
        if (intersection != null) {
          return intersection.fromPosition();
        }
        BlockPosition fromA = a.fromPosition();
        BlockPosition fromB = b.fromPosition();
        if (fromA.compareTo(fromB) < 0) {
          if (itA.hasNext()) {
            a = toRange(itA.next());
            continue;
          }
        } else {
          assert fromA.compareTo(fromB) > 0 : "fromA and fromB can't be equal";
          if (itB.hasNext()) {
            b = toRange(itB.next());
            continue;
          }
        }
        break;
      }
    }

    return null;
  }

  private static LiveRange toRange(Entry<BlockPosition, Integer> entry) {
    return new LiveRange(entry.getKey().block, entry.getKey().pos, entry.getValue());
  }

  private BlockPosition firstFrom() {
    return ranges.firstKey();
  }

  private BlockPosition lastTo() {
    return new BlockPosition(ranges.lastKey().block, Integer.MAX_VALUE);
  }

  private Iterator<Entry<BlockPosition, Integer>> iterateEntriesInRange(
      BlockPosition first, BlockPosition last) {
    NavigableMap<BlockPosition, Integer> subMapA =
        ranges.subMap(first, true, last, true); // both inclusive
    return subMapA.entrySet().iterator();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LinearLiveRanges that = (LinearLiveRanges) o;
    return Objects.equals(ranges, that.ranges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ranges);
  }

  @Override
  public String toString() {
    return "LinearLiveRanges{" + "ranges=" + ranges + '}';
  }
}
