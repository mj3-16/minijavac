package minijava.ir.assembler.allocation;

import static org.jooq.lambda.Seq.seq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.lifetime.LifetimeInterval.BlockInterval;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.tuple.Tuple2;

public class LinearScanAllocator {

  private final List<LifetimeInterval> unhandled;
  private final List<LifetimeInterval> inactive = new ArrayList<>();
  private final List<LifetimeInterval> active = new ArrayList<>();
  private final List<LifetimeInterval> handled = new ArrayList<>();
  private final Map<VirtualRegister, AMD64Register> allocation = new HashMap<>();

  public static final Comparator<LifetimeInterval> BY_FROM =
      Comparator.comparingInt((LifetimeInterval li) -> li.firstBlock().linearizedOrdinal)
          .thenComparingInt(li -> li.getLifetimeInBlock(li.firstBlock()).from);

  private LinearScanAllocator(List<LifetimeInterval> unhandled) {
    this.unhandled = unhandled;
  }

  private Map<VirtualRegister, AMD64Register> allocate(List<LifetimeInterval> unhandled) {
    for (LifetimeInterval current : unhandled) {
      CodeBlock first = current.firstBlock();
      BlockInterval firstInterval = current.getLifetimeInBlock(first);

      moveHandledAndInactiveFromActive(firstInterval);
      moveHandledAndActiveFromInactive(firstInterval);
      AMD64Register register = tryAllocateFreeRegister();
      if (register != null) {
        allocation.put(current.register, register);
      } else {
        // Allocation failed
        allocateBlockedRegister();
      }
    }
  }

  private void allocateBlockedRegister() {
    assert false;
  }

  @Nullable
  private AMD64Register tryAllocateFreeRegister(LifetimeInterval current) {
    BlockPosition definition = definition(current);
    BlockPosition lastUsage = lastUsage(current);
    Map<AMD64Register, BlockPosition> freeUntil = new HashMap<>();

    for (AMD64Register register : AMD64Register.values()) {
      freeUntil.put(register, new BlockPosition(current.lastBlock(), Integer.MAX_VALUE));
    }

    for (LifetimeInterval interval : active) {
      AMD64Register register = allocation.get(interval.register);
      assert register != null : "Active lifetime interval without allocated register";
      freeUntil.put(register, definition);
    }

    for (LifetimeInterval interval : inactive) {
      AMD64Register register = allocation.get(interval.register);
      assert register != null : "Inactive lifetime interval without allocated register";
      BlockInterval intersection = interval.firstIntersectionWith(current);
      if (intersection != null) {
        freeUntil.put(register, BlockPosition.fromBlockIntervalStart(intersection));
      }
    }

    Tuple2<AMD64Register, BlockPosition> bestCandidate = seq(freeUntil).maxBy(p -> p.v2).get();

    if (definition.equals(bestCandidate.v2)) {
      // There is no register available without spilling
      return null;
    }

    if (lastUsage.compareTo(bestCandidate.v2) < 0) {
      // There is a free register available for the whole interval
      return bestCandidate.v1;
    }

    // Otherwise we can assign an unused register, but we have to spill it before the original
    // usage kicks in. Thus, we have to split the lifetime interval.

    return AMD64Register.A;
  }

  private static BlockPosition definition(LifetimeInterval interval) {
    BlockInterval lifetimeInBlock = interval.getLifetimeInBlock(interval.firstBlock());
    return BlockPosition.fromBlockIntervalStart(lifetimeInBlock);
  }

  private static BlockPosition lastUsage(LifetimeInterval interval) {
    BlockInterval lifetimeInBlock = interval.getLifetimeInBlock(interval.lastBlock());
    return BlockPosition.fromBlockIntervalEnd(lifetimeInBlock);
  }

  private void moveHandledAndActiveFromInactive(BlockInterval firstInterval) {
    for (ListIterator<LifetimeInterval> it = inactive.listIterator(); it.hasNext(); ) {
      LifetimeInterval li = it.next();
      if (li.endsBefore(firstInterval)) {
        it.remove();
        handled.add(li);
      } else if (li.coversStartOf(firstInterval)) {
        it.remove();
        active.add(li);
      }
    }
  }

  private void moveHandledAndInactiveFromActive(BlockInterval firstInterval) {
    for (ListIterator<LifetimeInterval> it = active.listIterator(); it.hasNext(); ) {
      LifetimeInterval li = it.next();
      if (li.endsBefore(firstInterval)) {
        it.remove();
        handled.add(li);
      } else if (!li.coversStartOf(firstInterval)) {
        it.remove();
        inactive.add(li);
      }
    }
  }

  public static Map<VirtualRegister, AMD64Register> allocateRegisters(
      List<LifetimeInterval> unhandled) {
    unhandled.sort(BY_FROM);
    return new LinearScanAllocator(unhandled).allocate(unhandled);
  }

  private static class BlockPosition implements Comparable<BlockPosition> {
    private static Comparator<BlockPosition> COMPARATOR =
        Comparator.comparingInt((BlockPosition bp) -> bp.block.linearizedOrdinal)
            .thenComparingInt(bp -> bp.useDefIndex);
    public final CodeBlock block;
    public final int useDefIndex;

    private BlockPosition(CodeBlock block, int useDefIndex) {
      this.block = block;
      this.useDefIndex = useDefIndex;
    }

    public static BlockPosition fromBlockIntervalStart(BlockInterval interval) {
      return new BlockPosition(interval.block, interval.from);
    }

    public static BlockPosition fromBlockIntervalEnd(BlockInterval interval) {
      return new BlockPosition(interval.block, interval.from);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BlockPosition that = (BlockPosition) o;
      return useDefIndex == that.useDefIndex && Objects.equals(block, that.block);
    }

    @Override
    public int hashCode() {
      return Objects.hash(block, useDefIndex);
    }

    @Override
    public int compareTo(@NotNull BlockPosition other) {
      return COMPARATOR.compare(this, other);
    }
  }
}
