package minijava.ir.assembler.allocation;

import static org.jooq.lambda.Seq.seq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.lifetime.BlockInterval;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class LinearScanRegisterAllocator {

  private final SortedSet<LifetimeInterval> unhandled;
  private final List<LifetimeInterval> inactive = new ArrayList<>();
  private final List<LifetimeInterval> active = new ArrayList<>();
  private final List<LifetimeInterval> handled = new ArrayList<>();
  private final Map<LifetimeInterval, AMD64Register> allocation = new HashMap<>();
  private final Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes = new HashMap<>();
  // fields concerned with spilling
  private final SpillSlotAllocator spillSlotAllocator = new SpillSlotAllocator();

  public static final Comparator<LifetimeInterval> BY_FROM =
      Comparator.comparingInt((LifetimeInterval li) -> li.firstBlock().linearizedOrdinal)
          .thenComparingInt(li -> li.getLifetimeInBlock(li.firstBlock()).from);

  private LinearScanRegisterAllocator(List<LifetimeInterval> unhandled) {
    this.unhandled = new ConcurrentSkipListSet<>(BY_FROM);
    this.unhandled.addAll(unhandled);
  }

  private AllocationResult allocate() {
    for (LifetimeInterval current : unhandled) {
      CodeBlock first = current.firstBlock();
      BlockInterval firstInterval = current.getLifetimeInBlock(first);

      moveHandledAndInactiveFromActive(firstInterval);
      moveHandledAndActiveFromInactive(firstInterval);
      if (!tryAllocateFreeRegister(current)) {
        // Allocation failed
        allocateBlockedRegister(current);
      }
    }

    return new AllocationResult();
  }

  @Nullable
  private boolean tryAllocateFreeRegister(LifetimeInterval current) {
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
      if (intersection == null) {
        continue;
      }
      freeUntil.put(register, BlockPosition.fromBlockIntervalStart(intersection));
    }

    Tuple2<AMD64Register, BlockPosition> bestCandidate = seq(freeUntil).maxBy(p -> p.v2).get();
    AMD64Register assignedRegister = bestCandidate.v1;
    BlockPosition spillBefore = bestCandidate.v2;

    if (definition.equals(spillBefore)) {
      // There is no register available without spilling
      return false;
    }

    if (lastUsage.compareTo(spillBefore) < 0) {
      // There is a free register available for the whole interval
      assignRegister(current, assignedRegister);
      return true;
    }

    // Otherwise we can assign an unused register, but we have to spill it before the original
    // usage kicks in. Thus, we have to split the lifetime interval.
    splitReallocateProcrastinate(current, spillBefore, assignedRegister);
    return true;
  }

  private void assignRegister(LifetimeInterval interval, AMD64Register assignedRegister) {
    allocation.put(interval, assignedRegister);
    getLifetimeIntervals(interval.register).add(interval);
  }

  private List<LifetimeInterval> getLifetimeIntervals(VirtualRegister register) {
    return splitLifetimes.computeIfAbsent(register, k -> new ArrayList<>());
  }

  /** Only works if {@param interval} was the last split added. */
  private void deleteInterval(LifetimeInterval interval) {
    allocation.remove(interval);
    List<LifetimeInterval> splits = getLifetimeIntervals(interval.register);
    assert splits.indexOf(interval) == -1 || splits.indexOf(interval) == splits.size() - 1;
    splits.remove(interval);
  }

  private void allocateBlockedRegister(LifetimeInterval current) {
    BlockPosition definition = definition(current);
    BlockPosition firstUsage = firstUsage(current);

    Map<AMD64Register, BlockPosition> nextUse = new HashMap<>();
    for (AMD64Register register : AMD64Register.values()) {
      nextUse.put(register, new BlockPosition(current.lastBlock(), Integer.MAX_VALUE));
    }

    for (LifetimeInterval interval : active) {
      AMD64Register register = allocation.get(interval);
      // There will be a use after the current def, otherwise interval would not be active.
      BlockPosition nextUseAfterCurrentDef = interval.defAndUses.tailSet(definition).first();
      BlockPosition oldNext = nextUse.get(register);
      if (oldNext == null || oldNext.compareTo(nextUseAfterCurrentDef) > 0) {
        nextUse.put(register, nextUseAfterCurrentDef);
      }
    }

    for (LifetimeInterval interval : inactive) {
      AMD64Register register = allocation.get(interval);
      BlockInterval intersection = interval.firstIntersectionWith(current);
      if (intersection == null) {
        continue;
      }

      BlockPosition nextUseAfterCurrentDef = interval.defAndUses.tailSet(definition).first();
      BlockPosition oldNext = nextUse.get(register);
      if (oldNext == null || oldNext.compareTo(nextUseAfterCurrentDef) > 0) {
        nextUse.put(register, nextUseAfterCurrentDef);
      }
    }

    Tuple2<AMD64Register, BlockPosition> bestCandidate = seq(nextUse).maxBy(p -> p.v2).get();
    AMD64Register assignedRegister = bestCandidate.v1;
    BlockPosition farthestNextUse = bestCandidate.v2;

    if (firstUsage.compareTo(farthestNextUse) > 0) {
      // first usage is after any other conflicting interval's next usage.
      // current is to be spilled before its first interval ends.
      splitReallocateProcrastinate(current, firstUsage, assignedRegister);
    } else {
      // spill intervals that block the assignedRegister
      // First we split the active interval for assignedRegister.
      // This will delete the unsplit interval and instead re-add the first split part, assigned
      // to the old register, but will re-insert the other conflicting split half into unhandled.
      for (LifetimeInterval interval : filterByAllocatedRegister(active, assignedRegister)) {
        splitReallocateProcrastinate(interval, farthestNextUse, assignedRegister);
      }

      for (LifetimeInterval interval : filterByAllocatedRegister(inactive, assignedRegister)) {
        // We have to do the same for inactive intervals with the same assigned register.
        // If this intersects at some point with the current register, we have to split it and
        // re-insert the second half into unhandled.
        BlockInterval intersection = current.firstIntersectionWith(interval);
        if (intersection == null) {
          continue;
        }
        BlockPosition endOfLifetimeHole = BlockPosition.fromBlockIntervalStart(intersection);
        splitReallocateProcrastinate(interval, endOfLifetimeHole, assignedRegister);
      }
    }
  }

  private Seq<LifetimeInterval> filterByAllocatedRegister(
      Iterable<LifetimeInterval> intervals, AMD64Register register) {
    return seq(intervals).filter(li -> allocation.get(li) == register);
  }

  /**
   * Splits {@param current} at {@param splitPos}, deletes it from all book-keeping data structures,
   * re-allocates {@param register} to the first split half and 'procrastinates' the allocation
   * decision for the second, conflicting split graph by inserting it into the {@link #unhandled}
   * set.
   */
  private void splitReallocateProcrastinate(
      LifetimeInterval current, BlockPosition splitPos, AMD64Register register) {
    spillSlotAllocator.allocateSpillSlot(current.register);
    assert allocation.get(current) == null || allocation.get(current) == register
        : "Should reallocate the same register as the parent interval";
    deleteInterval(current);
    Tuple2<LifetimeInterval, LifetimeInterval> split = current.splitBefore(splitPos);
    LifetimeInterval allocated = split.v1;
    LifetimeInterval conflicting = split.v2;
    assignRegister(allocated, register);
    unhandled.add(conflicting);
  }

  private static BlockPosition definition(LifetimeInterval interval) {
    BlockInterval lifetimeInBlock = interval.getLifetimeInBlock(interval.firstBlock());
    return BlockPosition.fromBlockIntervalStart(lifetimeInBlock);
  }

  private static BlockPosition firstUsage(LifetimeInterval interval) {
    return interval.defAndUses.iterator().next();
  }

  private static BlockPosition lastUsage(LifetimeInterval interval) {
    return interval.defAndUses.last();
  }

  private void moveHandledAndActiveFromInactive(BlockInterval firstInterval) {
    for (ListIterator<LifetimeInterval> it = inactive.listIterator(); it.hasNext(); ) {
      LifetimeInterval li = it.next();
      if (li.endsBefore(firstInterval)) {
        it.remove();
        spillSlotAllocator.freeSpillSlot(li.register);
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
        spillSlotAllocator.freeSpillSlot(li.register);
        handled.add(li);
      } else if (!li.coversStartOf(firstInterval)) {
        it.remove();
        inactive.add(li);
      }
    }
  }

  public static AllocationResult allocateRegisters(List<LifetimeInterval> unhandled) {
    return new LinearScanRegisterAllocator(unhandled).allocate();
  }
}
