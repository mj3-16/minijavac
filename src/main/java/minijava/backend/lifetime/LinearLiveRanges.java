package minijava.backend.lifetime;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import minijava.backend.block.CodeBlock;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;

public class LinearLiveRanges {

  /** Maps from BlockPositions to end indices in the same block. */
  private final TreeMap<BlockPosition, Integer> ranges;

  public LinearLiveRanges() {
    this(new TreeMap<>());
  }

  private LinearLiveRanges(TreeMap<BlockPosition, Integer> ranges) {
    this.ranges = ranges;
  }

  @Nullable
  public BlockPosition from() {
    return ranges.isEmpty() ? null : ranges.firstKey();
  }

  @Nullable
  public BlockPosition to() {
    if (ranges.isEmpty()) {
      return null;
    }
    Entry<BlockPosition, Integer> entry = ranges.lastEntry();
    return new BlockPosition(entry.getKey().block, entry.getValue());
  }

  public List<LiveRange> getLiveRanges(CodeBlock block) {
    BlockPosition from = BlockPosition.beginOf(block);
    BlockPosition to = BlockPosition.endOf(block);
    List<LiveRange> ret = new ArrayList<>();
    for (Entry<BlockPosition, Integer> entry : ranges.subMap(from, true, to, true).entrySet()) {
      ret.add(new LiveRange(block, entry.getKey().pos, entry.getValue()));
    }
    return ret;
  }

  @Nullable
  public LiveRange getLiveRangeContaining(BlockPosition position) {
    LiveRange range = toRange(ranges.headMap(position, true).lastEntry());
    if (range != null && range.contains(position)) {
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

  public void deleteLiveRange(LiveRange range) {
    BlockPosition from = range.fromPosition();
    assert ranges.containsKey(from) && ranges.get(from).equals(range.to);
    ranges.remove(from);
  }

  public void deleteLiveRanges(CodeBlock block) {
    BlockPosition from = BlockPosition.beginOf(block);
    BlockPosition to = BlockPosition.endOf(block);
    ranges.subMap(from, to).clear();
  }

  public Split<LinearLiveRanges> splitBefore(BlockPosition beforePos) {
    TreeMap<BlockPosition, Integer> before = new TreeMap<>(this.ranges.headMap(beforePos, false));
    TreeMap<BlockPosition, Integer> after = new TreeMap<>(this.ranges.tailMap(beforePos, true));

    LiveRange needsSplit = getLiveRangeContaining(beforePos);
    if (needsSplit != null && needsSplit.fromPosition().compareTo(beforePos) < 0) {
      // There is a range containing beforePos that is currently only assigned to before. We need to split that up.
      // beforePos.pos can't be 0: Otherwise needsSplit would be in after since LiveRanges don't span across
      // block borders and it would have to start at beforePos.
      assert beforePos.pos > 0 : "beforePos.pos shouldn't be 0";
      LiveRange beforeRange = needsSplit.to(beforePos.pos - 1);
      LiveRange afterRange = needsSplit.from(beforePos.pos);
      before.remove(needsSplit.fromPosition());
      before.put(beforeRange.fromPosition(), beforeRange.to);
      after.put(afterRange.fromPosition(), afterRange.to);
    }

    return new Split<>(new LinearLiveRanges(before), new LinearLiveRanges(after));
  }

  public void addAllRanges(LinearLiveRanges other) {
    ranges.putAll(other.ranges);
    coalesceAdjacentRanges();
  }

  private void coalesceAdjacentRanges() {
    Iterator<Entry<BlockPosition, Integer>> it = new TreeMap<>(ranges).entrySet().iterator();
    if (!it.hasNext()) {
      return;
    }
    Map.Entry<BlockPosition, Integer> current = it.next();
    ranges.clear();
    while (it.hasNext()) {
      Map.Entry<BlockPosition, Integer> next = it.next();
      BlockPosition consecutivePos =
          new BlockPosition(current.getKey().block, current.getValue() + 1);
      assert consecutivePos.compareTo(next.getKey()) <= 0 : "LiveRanges are overlapping";
      if (next.getKey().equals(consecutivePos)) {
        current.setValue(next.getValue());
      } else {
        ranges.put(current.getKey(), current.getValue());
        current = next;
      }
    }
    ranges.put(current.getKey(), current.getValue());
  }

  @Nullable
  public BlockPosition firstIntersectionWith(LinearLiveRanges other) {
    if (ranges.isEmpty() || other.ranges.isEmpty()) {
      return null;
    }

    BlockPosition first = Seq.of(from(), other.from()).max().get();
    BlockPosition last = Seq.of(to(), other.to()).min().get();

    if (first.compareTo(last) > 0) {
      return null;
    }

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
    if (entry == null) {
      return null;
    }
    return new LiveRange(entry.getKey().block, entry.getKey().pos, entry.getValue());
  }

  private Iterator<Entry<BlockPosition, Integer>> iterateEntriesInRange(
      BlockPosition first, BlockPosition last) {
    LiveRange range = getLiveRangeContaining(first);
    if (range != null) {
      // There's a live range which starts before first that contains it.
      // We also have to iterate over that.
      first = range.fromPosition();
    }
    return ranges.subMap(first, true, last, true).entrySet().iterator();
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
    return Iterables.toString(seq(ranges.entrySet()).map(LinearLiveRanges::toRange));
  }
}
