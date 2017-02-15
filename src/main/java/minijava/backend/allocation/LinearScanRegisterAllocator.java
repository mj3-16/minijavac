package minijava.backend.allocation;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Function;
import minijava.backend.block.CodeBlock;
import minijava.backend.lifetime.BlockPosition;
import minijava.backend.lifetime.FixedInterval;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.lifetime.Split;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.Register;
import minijava.backend.registers.VirtualRegister;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class LinearScanRegisterAllocator {

  // Inputs to the algorithm
  private final Set<AMD64Register> allocatable;
  private final Map<AMD64Register, FixedInterval> fixedIntervals;
  private final SortedSet<LifetimeInterval> unhandled;

  // State of the algorithm
  private final List<LifetimeInterval> inactive = new ArrayList<>();
  private final List<LifetimeInterval> active = new ArrayList<>();

  // Outputs
  private final Map<LifetimeInterval, AMD64Register> allocation = new HashMap<>();
  private final Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes = new HashMap<>();
  private final SpillSlotAllocator spillSlotAllocator = new SpillSlotAllocator();

  private LinearScanRegisterAllocator(
      LifetimeAnalysisResult lifetimes, Set<AMD64Register> allocatable) {
    this.fixedIntervals = lifetimes.fixedIntervals;
    this.allocatable = allocatable;
    this.unhandled = new ConcurrentSkipListSet<>(LifetimeInterval.COMPARING_DEF);
    this.unhandled.addAll(lifetimes.virtualIntervals.values());
  }

  private AllocationResult allocate() {
    for (LifetimeInterval current : unhandled) {
      CodeBlock first = current.firstBlock();
      BlockPosition startPosition = current.getLifetimeInBlock(first).fromPosition();

      moveHandledAndInactiveFromActive(startPosition);
      moveHandledAndActiveFromInactive(startPosition);
      if (!tryAllocateFreeRegister(current)) {
        // Allocation failed
        allocateBlockedRegister(current);
      }
    }

    return new AllocationResult(allocation, splitLifetimes, spillSlotAllocator.spillSlots);
  }

  private boolean tryAllocateFreeRegister(LifetimeInterval current) {
    BlockPosition start = current.from();
    BlockPosition end = current.to();
    System.out.println("start = " + start);
    Map<AMD64Register, ConflictSite> freeUntil = new TreeMap<>();

    for (AMD64Register register : allocatable) {
      FixedInterval fixed = fixedIntervals.get(register);
      BlockPosition blockedAt = fixed.ranges.firstIntersectionWith(current.ranges);
      putEarliest(freeUntil, register, ConflictSite.atOrNever(blockedAt));
    }

    for (LifetimeInterval interval : active) {
      AMD64Register register = allocation.get(interval);
      assert register != null : "Active lifetime interval without allocated register";
      putEarliest(freeUntil, register, ConflictSite.at(start));
    }

    for (LifetimeInterval interval : inactive) {
      AMD64Register register = allocation.get(interval);
      assert register != null : "Inactive lifetime interval without allocated register";
      BlockPosition endOfLifetimeHole = interval.ranges.firstIntersectionWith(current.ranges);
      if (endOfLifetimeHole == null) {
        continue;
      }
      putEarliest(freeUntil, register, ConflictSite.at(endOfLifetimeHole));
    }

    Tuple2<AMD64Register, ConflictSite> bestCandidate = determineBestCandidate(freeUntil, current);
    System.out.println(bestCandidate + " for " + current.register);
    AMD64Register assignedRegister = bestCandidate.v1;
    ConflictSite conflict = bestCandidate.v2;

    if (ConflictSite.at(start).equals(conflict)) {
      // There is no register available without spilling
      return false;
    }

    if (ConflictSite.at(end).compareTo(conflict) < 0) {
      // There is a free register available for the whole interval
      assignRegister(current, assignedRegister);
      return true;
    }

    // Otherwise we can assign an unused register, but we have to spill it before the original
    // usage kicks in. Thus, we have to split the lifetime interval.
    LifetimeInterval before = spillSplitAndSuspendBeforeConflict(current, conflict);
    assignRegister(before, assignedRegister);
    return true;
  }

  private void putEarliest(
      Map<AMD64Register, ConflictSite> freeUntil, AMD64Register register, ConflictSite newSite) {
    System.out.println("newSite = " + newSite);
    ConflictSite old = freeUntil.computeIfAbsent(register, k -> ConflictSite.never());
    if (old.compareTo(newSite) > 0) {
      freeUntil.put(register, newSite);
    }
  }

  private Tuple2<AMD64Register, ConflictSite> determineBestCandidate(
      Map<AMD64Register, ConflictSite> nextConflicts, LifetimeInterval current) {
    Tuple2<AMD64Register, ConflictSite> bestCandidate = seq(nextConflicts).maxBy(t -> t.v2).get();
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
            .filter(allocatable::contains)
            .toList();

    for (AMD64Register locked : order) {
      ConflictSite conflict = nextConflicts.get(locked);
      if (!conflict.doesConflictAtAll()) {
        // There was no use of that register (before a back edge ...).
        // This is a good candidate nonetheless.
        return tuple(locked, conflict);
      }

      boolean goodEnough = current.endsBefore(conflict.conflictingPosition());
      boolean notWorseThanBest = bestCandidate.v2.equals(conflict);
      if (goodEnough || notWorseThanBest) {
        // locked is a better candidate
        return tuple(locked, conflict);
      }
    }

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

  private void assignRegister(LifetimeInterval interval, @NotNull AMD64Register assignedRegister) {
    AMD64Register old = allocation.put(interval, assignedRegister);
    assert old != assignedRegister : "Can't reassign a register here";
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
    assert old.register.equals(new_.register);
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
    System.out.println();
    System.out.println("LinearScanRegisterAllocator.allocateBlockedRegister");
    BlockPosition start = current.from();
    System.out.println("start = " + start);

    Map<AMD64Register, ConflictSite> nextUse = new HashMap<>();
    for (AMD64Register register : allocatable) {
      putEarliest(nextUse, register, ConflictSite.never());
    }

    for (LifetimeInterval interval : Seq.concat(active, inactive)) {
      AMD64Register register = allocation.get(interval);
      System.out.println("register = " + register);
      System.out.println("interval.ranges = " + interval.ranges);
      System.out.println("current.ranges = " + current.ranges);
      BlockPosition conflict = interval.ranges.firstIntersectionWith(current.ranges);
      System.out.println("conflict = " + conflict);
      if (conflict == null) {
        // Must be an non-conflicting inactive interval
        continue;
      }

      BlockPosition nextUseAfterCurrentDef = interval.nextUseAfter(start);
      if (nextUseAfterCurrentDef == null) {
        // No further uses, but it conflicts! This happens for loop invariant definitions, which
        // are still alive after their last use.
        // In this case, we can just spill the value and re-use its register. If the back edge
        // is taken, we reload the spilled value and if not, we have the new value in a register.
        // So we just assume no use from this interval.
        continue;
      }

      putEarliest(nextUse, register, ConflictSite.at(nextUseAfterCurrentDef));
    }

    System.out.println("nextUse = " + nextUse);
    Tuple2<AMD64Register, ConflictSite> bestCandidate = determineBestCandidate(nextUse, current);
    System.out.println("bestCandidate = " + bestCandidate);
    AMD64Register assignedRegister = bestCandidate.v1;
    ConflictSite farthestNextUse = bestCandidate.v2;

    // nextUsage might not conflict if start was the last usage (which can only happen if current was split)
    ConflictSite nextUsage = ConflictSite.atOrNever(current.nextUseAfter(start));
    if (false && nextUsage.doesConflictAtAll()) {
      // TODO: We don't do this yet, because we can't say if the next use needs a RegisterOperand or not.
      // This happens when current is the result of the split. Otherwise we should always have a definition and at least
      // one use.
      assert current.firstDefOrUse().isUse() : "Assumed the register was split";
      // When this interval was split, it was also assigned a spill slot.
      // By not assigning any register, we can make the lowering step use a MemoryOperand for the use instead.
    } else if (false && nextUsage.compareTo(farthestNextUse) > 0) {
      // TODO: We don't do this yet, because we can't say if the next use needs a RegisterOperand or not.
      // first usage is after any other conflicting interval's next usage.
      // current is to be spilled immediately after its definition.
      // Note that it's crucial that we use a MemoryOperand instruction when spilling, because
      // we don't have enough registers.
      // Actually we should split before the next use which needs a RegisterOperand.
      LifetimeInterval before = spillSplitAndSuspendBeforeConflict(current, nextUsage);
      // This will have assigned a spill slot, but the before part is not present anywhere in our data structures.
      // We don't assign it a register, but we still have to note that it's part of the splits.
      getLifetimeIntervals(before.register).add(before);
    } else if (farthestNextUse.doesConflictAtAll()
        && start.equals(farthestNextUse.conflictingPosition())) {
      // Note that it holds that start = farthestNextUse => nextUsage > farthestNextUse.
      // We need this fallback for massive numbers of Phis, so that all hardware registers are needed simultaneously.
      // If I ever come around to fixing those TODOs above, this shouldn't be needed any more.
      // We try to split at the next usage, where we try to assign a register again.
      current = spillSplitAndSuspendBeforeConflict(current, nextUsage);
      // For the remaining interval, we have to do everything with MemoryOperands.
      spillSlotAllocator.allocateSpillSlot(current.register);
      getLifetimeIntervals(current.register).add(current);
    } else {
      // spill intervals that block the assignedRegister
      // First we split the active interval for assignedRegister.
      // This will delete the unsplit interval and instead re-add the first split part, assigned
      // to the old register, but will re-insert the other conflicting split half into unhandled.
      for (LifetimeInterval interval : filterByAllocatedRegister(active, assignedRegister)) {
        System.out.println("Splitting " + interval.register + " at " + farthestNextUse);
        LifetimeInterval before = spillSplitAndSuspendBeforeConflict(interval, farthestNextUse);
        renameInterval(interval, before);
      }

      for (LifetimeInterval interval : filterByAllocatedRegister(inactive, assignedRegister)) {
        // We have to do the same for inactive intervals with the same assigned register.
        // If this intersects at some point with the current register, we have to split it and
        // re-insert the second half into unhandled.
        ConflictSite endOfLifetimeHole =
            ConflictSite.atOrNever(current.ranges.firstIntersectionWith(interval.ranges));
        if (!endOfLifetimeHole.doesConflictAtAll()) {
          continue;
        }
        LifetimeInterval before = spillSplitAndSuspendBeforeConflict(interval, endOfLifetimeHole);
        renameInterval(interval, before);
      }

      // This will swap out all other intervals currently active for assignedRegister.
      assignRegister(current, assignedRegister);

      FixedInterval fixed = fixedIntervals.get(assignedRegister);
      if (fixed != null) {
        ConflictSite constraint =
            ConflictSite.atOrNever(fixed.ranges.firstIntersectionWith(current.ranges));
        if (constraint.doesConflictAtAll()) {
          // A register constraint kicks in at constraint, so we have to split current (again).
          spillSplitAndSuspendBeforeConflict(current, constraint);
        }
      }
    }
  }

  private List<LifetimeInterval> filterByAllocatedRegister(
      Iterable<LifetimeInterval> intervals, AMD64Register register) {
    return seq(intervals).filter(li -> allocation.get(li) == register).toList();
  }

  /**
   * Splits {@param current} at {@param splitPos}, reallocates registers to the first split half and
   * suspends the allocation decision for the second, conflicting split graph by inserting it into
   * the {@link #unhandled} set.
   *
   * @return The first part of the split.
   */
  private LifetimeInterval spillSplitAndSuspendBeforeConflict(
      LifetimeInterval current, ConflictSite conflict) {
    System.out.println("conflict = " + conflict);
    spillSlotAllocator.allocateSpillSlot(current.register);
    if (!conflict.doesConflictAtAll()
        || current.to().compareTo(conflict.conflictingPosition()) < 0) {
      // current doesn't even intersect splitPos. We can just return it itself.
      // This does happen for loop invariant definitions, where the last use doesn't coincide with
      // the end of life. When we want to split it after its 'last use', we use something that
      // certainly will not intersect with the current interval.
      // We will need to allocate a spill slot for the interval to be loaded again when the back
      // edge is followed.
      return current;
    }
    Split<LifetimeInterval> split = current.splitBefore(conflict.conflictingPosition());
    unhandled.add(split.after);
    return split.before;
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
    return allocateRegisters(lifetimes, AMD64Register.allocatable);
  }

  public static AllocationResult allocateRegisters(
      LifetimeAnalysisResult lifetimes, Set<AMD64Register> allocatable) {
    return new LinearScanRegisterAllocator(lifetimes, allocatable).allocate();
  }
}
