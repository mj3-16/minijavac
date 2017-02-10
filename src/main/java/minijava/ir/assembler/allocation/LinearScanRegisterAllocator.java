package minijava.ir.assembler.allocation;

import static org.jooq.lambda.Seq.seq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.lifetime.*;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class LinearScanRegisterAllocator {

  // Inputs to the algorithm
  private final Map<AMD64Register, FixedInterval> fixedIntervals;
  private final SortedSet<LifetimeInterval> unhandled;

  // State of the algorithm
  private final List<LifetimeInterval> inactive = new ArrayList<>();
  private final List<LifetimeInterval> active = new ArrayList<>();
  private final List<LifetimeInterval> handled = new ArrayList<>();

  // Outputs
  private final Map<LifetimeInterval, AMD64Register> allocation = new HashMap<>();
  private final Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes = new HashMap<>();
  private final SpillSlotAllocator spillSlotAllocator = new SpillSlotAllocator();

  private LinearScanRegisterAllocator(LifetimeAnalysisResult lifetimes) {
    this.fixedIntervals = lifetimes.fixedIntervals;
    this.unhandled = new ConcurrentSkipListSet<>(LifetimeInterval.COMPARING_DEF);
    this.unhandled.addAll(lifetimes.virtualIntervals);
  }

  private AllocationResult allocate() {
    for (LifetimeInterval current : unhandled) {
      CodeBlock first = current.firstBlock();
      LiveRange firstInterval = current.getLifetimeInBlock(first);

      moveHandledAndInactiveFromActive(firstInterval);
      moveHandledAndActiveFromInactive(firstInterval);
      if (!tryAllocateFreeRegister(current)) {
        // Allocation failed
        allocateBlockedRegister(current);
      }
    }

    return new AllocationResult(allocation, splitLifetimes, spillSlotAllocator.spillSlots);
  }

  private boolean tryAllocateFreeRegister(LifetimeInterval current) {
    BlockPosition definition = definition(current);
    BlockPosition lastUsage = lastUsage(current);
    Map<AMD64Register, BlockPosition> freeUntil = new HashMap<>();

    for (AMD64Register register : AMD64Register.allocatable) {
      FixedInterval fixed = fixedIntervals.get(register);
      BlockPosition blockedAt = fixed.ranges.firstIntersectionWith(current.ranges);
      if (blockedAt == null) {
        // We freely choose a position after the last usage
        blockedAt = new BlockPosition(current.lastBlock(), Integer.MAX_VALUE);
      }
      freeUntil.put(register, blockedAt);
    }

    for (LifetimeInterval interval : active) {
      AMD64Register register = allocation.get(interval);
      assert register != null : "Active lifetime interval without allocated register";
      freeUntil.put(register, definition);
    }

    for (LifetimeInterval interval : inactive) {
      AMD64Register register = allocation.get(interval);
      assert register != null : "Inactive lifetime interval without allocated register";
      BlockPosition endOfLifetimeHole = interval.ranges.firstIntersectionWith(current.ranges);
      if (endOfLifetimeHole == null) {
        continue;
      }
      freeUntil.put(register, endOfLifetimeHole);
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
      BlockPosition endOfLifetimeHole = interval.ranges.firstIntersectionWith(current.ranges);
      if (endOfLifetimeHole == null) {
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
        BlockPosition endOfLifetimeHole = current.ranges.firstIntersectionWith(interval.ranges);
        if (endOfLifetimeHole == null) {
          continue;
        }
        splitReallocateProcrastinate(interval, endOfLifetimeHole, assignedRegister);
      }

      assignRegister(current, assignedRegister);
    }

    FixedInterval fixed = fixedIntervals.get(assignedRegister);
    BlockPosition constraintPosition = fixed.ranges.firstIntersectionWith(current.ranges);
    if (constraintPosition != null) {
      // A register constrained kicks in at constraintPosition, so we have to split current (again).
      splitReallocateProcrastinate(current, constraintPosition, assignedRegister);
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
    Split<LifetimeInterval> split = current.splitBefore(splitPos);
    LifetimeInterval allocated = split.before;
    LifetimeInterval conflicting = split.after;
    assignRegister(allocated, register);
    unhandled.add(conflicting);
  }

  private static BlockPosition definition(LifetimeInterval interval) {
    LiveRange lifetimeInBlock = interval.getLifetimeInBlock(interval.firstBlock());
    return lifetimeInBlock.fromPosition();
  }

  private static BlockPosition firstUsage(LifetimeInterval interval) {
    return interval.defAndUses.first();
  }

  private static BlockPosition lastUsage(LifetimeInterval interval) {
    return interval.defAndUses.last();
  }

  private void moveHandledAndActiveFromInactive(LiveRange firstInterval) {
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

  private void moveHandledAndInactiveFromActive(LiveRange firstInterval) {
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

  public static AllocationResult allocateRegisters(LifetimeAnalysisResult lifetimes) {
    return new LinearScanRegisterAllocator(lifetimes).allocate();
  }
}
