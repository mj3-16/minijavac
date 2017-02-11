package minijava.ir.assembler.allocation;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.google.common.collect.Sets;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.lifetime.*;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class LinearScanRegisterAllocator {

  // Inputs to the algorithm
  private final Map<AMD64Register, FixedInterval> fixedIntervals;
  private final SortedSet<LifetimeInterval> unhandled;

  // State of the algorithm
  private final List<LifetimeInterval> inactive = new ArrayList<>();
  private final List<LifetimeInterval> active = new ArrayList<>();

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
    System.out.println();
    for (LifetimeInterval current : unhandled) {
      System.out.println();
      System.out.println(current);
      CodeBlock first = current.firstBlock();
      BlockPosition startPosition = current.getLifetimeInBlock(first).fromPosition();

      moveHandledAndInactiveFromActive(startPosition);
      moveHandledAndActiveFromInactive(startPosition);
      System.out.println(active);
      System.out.println(inactive);
      System.out.println();
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
    Map<AMD64Register, BlockPosition> freeUntil = new TreeMap<>();

    for (AMD64Register register : AMD64Register.allocatable) {
      FixedInterval fixed = fixedIntervals.get(register);
      BlockPosition blockedAt = fixed.ranges.firstIntersectionWith(current.ranges);
      if (blockedAt == null) {
        // We freely choose a position after the last usage
        blockedAt = BlockPosition.endOf(current.lastBlock());
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

    Tuple2<AMD64Register, BlockPosition> bestCandidate = determineBestCandidate(freeUntil, current);
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
    LifetimeInterval before = splitAndSuspendAfterHalf(current, spillBefore).before;
    assignRegister(before, assignedRegister);
    return true;
  }

  private Tuple2<AMD64Register, BlockPosition> determineBestCandidate(
      Map<AMD64Register, BlockPosition> nextBlocked, LifetimeInterval current) {
    Tuple2<AMD64Register, BlockPosition> bestCandidate = seq(nextBlocked).maxBy(p -> p.v2).get();
    // bestCandidate might not respect register hints. We'd like to preserve them if at all possible.

    Set<AMD64Register> lockedFromHints =
        getLockedHints(current.fromHints, current.register, this::getLastSplitLifetime);
    Set<AMD64Register> lockedToHints =
        getLockedHints(current.toHints, current.register, this::getFirstSplitLifetime);
    // This order will favor locked registers mentioned in both toHints and fromHints.
    List<AMD64Register> order =
        seq(Sets.union(lockedToHints, lockedFromHints))
            .append(lockedToHints)
            .append(lockedFromHints)
            .distinct()
            .toList();

    for (AMD64Register locked : order) {
      BlockPosition blocked = nextBlocked.get(locked);
      boolean goodEnough = current.endsBefore(blocked);
      boolean notWorseThanBest = bestCandidate.v2.equals(blocked);
      if (goodEnough || notWorseThanBest) {
        // locked is a better candidate
        System.out.println("locked = " + locked);
        return tuple(locked, blocked);
      }
    }

    System.out.println("bestCandidate = " + bestCandidate);
    return bestCandidate;
  }

  @NotNull
  private Set<AMD64Register> getLockedHints(
      Set<Register> hints,
      VirtualRegister ownRegister,
      Function<VirtualRegister, LifetimeInterval> relevantSplit) {
    Set<AMD64Register> lockedHints = new TreeSet<>();

    for (Register hint : hints) {
      if (hint.equals(ownRegister)) {
        continue;
      }
      AMD64Register locked =
          hint instanceof AMD64Register
              ? (AMD64Register) hint
              : allocation.get(relevantSplit.apply((VirtualRegister) hint));
      if (locked != null) {
        lockedHints.add(locked);
      }
    }
    return lockedHints;
  }

  private LifetimeInterval getLastSplitLifetime(VirtualRegister hint) {
    List<LifetimeInterval> lifetimes = splitLifetimes.get(hint);
    if (lifetimes == null || lifetimes.size() == 0) {
      return null;
    }
    return lifetimes.get(lifetimes.size() - 1);
  }

  private LifetimeInterval getFirstSplitLifetime(VirtualRegister hint) {
    List<LifetimeInterval> lifetimes = splitLifetimes.get(hint);
    if (lifetimes == null || lifetimes.size() == 0) {
      return null;
    }
    return lifetimes.get(lifetimes.size() - 1);
  }

  private void assignRegister(LifetimeInterval interval, AMD64Register assignedRegister) {
    AMD64Register old = allocation.put(interval, assignedRegister);
    assert old == null : "Can't reassign a register here";
    getLifetimeIntervals(interval.register).add(interval);
    List<LifetimeInterval> sameAssignment =
        seq(active).filter(li -> allocation.get(li) == assignedRegister).toList();
    active.removeAll(sameAssignment);
    active.add(interval);
  }

  private List<LifetimeInterval> getLifetimeIntervals(VirtualRegister register) {
    return splitLifetimes.computeIfAbsent(register, k -> new ArrayList<>());
  }

  /** Only works if {@param old} was the last split added. */
  private void renameInterval(LifetimeInterval old, LifetimeInterval new_) {
    allocation.put(new_, allocation.remove(old));
    List<LifetimeInterval> splits = getLifetimeIntervals(old.register);
    int idx = splits.indexOf(old);
    assert idx == -1 || idx == splits.size() - 1;
    replaceIfPresent(splits, old, new_);
    replaceIfPresent(active, old, new_);
    replaceIfPresent(inactive, old, new_);
  }

  private <T> void replaceIfPresent(List<T> where, T what, T replacement) {
    int idx = where.indexOf(what);
    if (idx >= 0) {
      where.set(idx, replacement);
    }
  }

  private void allocateBlockedRegister(LifetimeInterval current) {
    BlockPosition definition = definition(current);
    BlockPosition firstUsage = firstUsage(current);

    Map<AMD64Register, BlockPosition> nextUse = new HashMap<>();
    for (AMD64Register register : AMD64Register.values()) {
      nextUse.put(register, BlockPosition.endOf(current.lastBlock()));
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

    Tuple2<AMD64Register, BlockPosition> bestCandidate = determineBestCandidate(nextUse, current);
    AMD64Register assignedRegister = bestCandidate.v1;
    BlockPosition farthestNextUse = bestCandidate.v2;

    if (firstUsage.compareTo(farthestNextUse) > 0) {
      // first usage is after any other conflicting interval's next usage.
      // current is to be spilled before its first interval ends.
      // Note that it's crucial that we use a MemoryOperand instruction when spilling, because
      // we don't have enough registers.
      splitAndSuspendAfterHalf(current, firstUsage);
      // This will have assigned a spill slot.
    } else {
      // spill intervals that block the assignedRegister
      // First we split the active interval for assignedRegister.
      // This will delete the unsplit interval and instead re-add the first split part, assigned
      // to the old register, but will re-insert the other conflicting split half into unhandled.
      for (LifetimeInterval interval : filterByAllocatedRegister(active, assignedRegister)) {
        splitAndSuspendAfterHalf(interval, farthestNextUse);
        // This will have reassigned the register to the first half and suspended the other half
        // to be handled later.
      }

      for (LifetimeInterval interval : filterByAllocatedRegister(inactive, assignedRegister)) {
        // We have to do the same for inactive intervals with the same assigned register.
        // If this intersects at some point with the current register, we have to split it and
        // re-insert the second half into unhandled.
        BlockPosition endOfLifetimeHole = current.ranges.firstIntersectionWith(interval.ranges);
        if (endOfLifetimeHole == null) {
          continue;
        }
        splitAndSuspendAfterHalf(interval, endOfLifetimeHole);
        // This will have reassigned the register to the first half and suspended the other half
        // to be handled later.
      }

      // This will swap out all other intervals currently active for assignedRegister.
      assignRegister(current, assignedRegister);
    }

    FixedInterval fixed = fixedIntervals.get(assignedRegister);
    BlockPosition constraintPosition = fixed.ranges.firstIntersectionWith(current.ranges);
    if (constraintPosition != null) {
      // A register constrained kicks in at constraintPosition, so we have to split current (again).
      splitAndSuspendAfterHalf(current, constraintPosition);
    }
  }

  private Seq<LifetimeInterval> filterByAllocatedRegister(
      Iterable<LifetimeInterval> intervals, AMD64Register register) {
    return seq(intervals).filter(li -> allocation.get(li) == register);
  }

  /**
   * Splits {@param current} at {@param splitPos}, reallocates registers to the first split half and
   * suspends the allocation decision for the second, conflicting split graph by inserting it into
   * the {@link #unhandled} set.
   */
  private Split<LifetimeInterval> splitAndSuspendAfterHalf(
      LifetimeInterval current, BlockPosition splitPos) {
    spillSlotAllocator.allocateSpillSlot(current.register);
    Split<LifetimeInterval> split = current.splitBefore(splitPos);
    renameInterval(current, split.before);
    LifetimeInterval conflicting = split.after;
    unhandled.add(conflicting);
    return split;
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

  private void moveHandledAndActiveFromInactive(BlockPosition position) {
    for (ListIterator<LifetimeInterval> it = inactive.listIterator(); it.hasNext(); ) {
      LifetimeInterval li = it.next();
      if (li.endsBefore(position)) {
        it.remove();
        spillSlotAllocator.freeSpillSlot(li.register);
      } else if (li.covers(position)) {
        it.remove();
        active.add(li);
      }
    }
  }

  private void moveHandledAndInactiveFromActive(BlockPosition position) {
    for (ListIterator<LifetimeInterval> it = active.listIterator(); it.hasNext(); ) {
      LifetimeInterval li = it.next();
      if (li.endsBefore(position)) {
        it.remove();
        spillSlotAllocator.freeSpillSlot(li.register);
      } else if (!li.covers(position)) {
        it.remove();
        inactive.add(li);
      }
    }
  }

  public static AllocationResult allocateRegisters(LifetimeAnalysisResult lifetimes) {
    return new LinearScanRegisterAllocator(lifetimes).allocate();
  }
}
