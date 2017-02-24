package minijava.backend.allocation;

import static minijava.backend.lifetime.LifetimeInterval.coalesceIntervals;
import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import minijava.backend.block.CodeBlock;
import minijava.backend.lifetime.BlockPosition;
import minijava.backend.lifetime.FixedInterval;
import minijava.backend.lifetime.LifetimeAnalysisResult;
import minijava.backend.lifetime.LifetimeInterval;
import minijava.backend.lifetime.LiveRange;
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
    this.unhandled = new TreeSet<>(LifetimeInterval.COMPARING_DEF);
    this.unhandled.addAll(lifetimes.virtualIntervals.values());
  }

  private AllocationResult allocate() {
    BlockPosition last = null;
    while (!unhandled.isEmpty()) {
      assert unhandled.size() == seq(unhandled).map(li -> li.register).count();
      LifetimeInterval current = unhandled.first();
      unhandled.remove(current);
      System.out.println();
      System.out.println("LinearScanRegisterAllocator.allocate");
      System.out.println(current);
      CodeBlock first = current.firstBlock();
      LiveRange rangeInFirstBlock = current.getLifetimeInBlock(first);
      assert rangeInFirstBlock != null : "The interval should be alive in its first block";
      BlockPosition startPosition = rangeInFirstBlock.fromPosition();
      assert last == null || startPosition.compareTo(last) >= 0;
      last = startPosition;

      moveHandledAndInactiveFromActive(startPosition);
      moveHandledAndActiveFromInactive(startPosition);
      if (!tryAllocateFreeRegister(current)) {
        // Allocation failed
        allocateBlockedRegister(current);
      }
    }

    mergeAndUnassignIntervalsWithoutUsages();
    //optimizeSplitPositions();

    return new AllocationResult(allocation, splitLifetimes, spillSlotAllocator.spillSlots);
  }

  private boolean tryAllocateFreeRegister(LifetimeInterval current) {
    BlockPosition start = current.from();
    BlockPosition end = current.to();
    System.out.println();
    System.out.println("LinearScanRegisterAllocator.tryAllocateFreeRegister");
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

    System.out.println("freeUntil = " + freeUntil);

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
    System.out.println("idx = " + idx);
    System.out.println("splits.size() = " + splits.size());
    System.out.println("splits = " + splits);
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
    System.out.println("current = " + current);
    BlockPosition start = current.from();

    Map<AMD64Register, ConflictSite> nextUses = new HashMap<>();
    for (AMD64Register register : allocatable) {
      BlockPosition conflict =
          fixedIntervals.get(register).ranges.firstIntersectionWith(current.ranges);
      putEarliest(nextUses, register, ConflictSite.atOrNever(conflict));
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

      System.out.println("nextUseAfterCurrentDef = " + nextUseAfterCurrentDef);
      putEarliest(nextUses, register, ConflictSite.at(nextUseAfterCurrentDef));
    }

    System.out.println("nextUses = " + nextUses);
    Tuple2<AMD64Register, ConflictSite> bestCandidate = determineBestCandidate(nextUses, current);
    System.out.println("bestCandidate = " + bestCandidate);
    AMD64Register assignedRegister = bestCandidate.v1;
    ConflictSite farthestNextUse = bestCandidate.v2;

    // firstUse might not conflict if there aren't any further uses (e.g. if the interval was split)
    ConflictSite firstUse = ConflictSite.atOrNever(current.firstUse());
    ConflictSite firstRegUse = ConflictSite.atOrNever(current.firstUseNeedingARegister());
    if (!firstUse.doesConflictAtAll()) {
      // There were no uses in the interval, so we can just lay it dormant in a spill slot.
      spillSlotAllocator.allocateSpillSlot(current.register);
      getLifetimeIntervals(current.register).add(current);
    } else if (firstUse.compareTo(farthestNextUse) > 0
        || firstUse.equals(farthestNextUse) && firstRegUse.compareTo(farthestNextUse) > 0) {
      // first usage is after any other conflicting interval's next usage, or it is used at
      // any other interval's next usage but we can use a MemoryOperand.
      // current is to be spilled immediately after its definition (which probably is in a prior
      // interval).
      LifetimeInterval before = spillSplitAndSuspendBeforeConflict(current, firstRegUse);
      // This will have assigned a spill slot, but the before part is not present anywhere in our data structures.
      // We don't assign it a register, but we still have to note that it's part of the splits.
      getLifetimeIntervals(before.register).add(before);
    } else {
      // spill intervals that block the assignedRegister
      // First we split the active interval for assignedRegister.
      // This will delete the unsplit interval and instead re-add the first split part, assigned
      // to the old register, but will re-insert the other conflicting split half into unhandled.
      for (LifetimeInterval interval : filterByAllocatedRegister(active, assignedRegister)) {
        // FIXME: what if interval starts at start?
        System.out.println(interval);
        System.out.println("Splitting " + interval.register + " at " + start);
        System.out.println(current.ranges.firstIntersectionWith(interval.ranges));
        // We split it at start, reflecting the fact that at this position there is no longer
        // a register assigned.
        LifetimeInterval before =
            spillSplitAndSuspendBeforeConflict(interval, ConflictSite.at(start));
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
          System.out.println("Fixed interval split");
          LifetimeInterval before = spillSplitAndSuspendBeforeConflict(current, constraint);
          renameInterval(current, before);
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
    assert !conflict.doesConflictAtAll()
        || conflict.conflictingPosition().compareTo(current.from()) >= 0;
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
    assert unhandled.add(split.after);
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

  private void mergeAndUnassignIntervalsWithoutUsages() {
    for (List<LifetimeInterval> splits : splitLifetimes.values()) {
      List<LifetimeInterval> oldSplits = new ArrayList<>(splits);
      Iterator<LifetimeInterval> it = oldSplits.iterator();
      List<LifetimeInterval> toMerge = new ArrayList<>();
      splits.clear();
      while (it.hasNext()) {
        LifetimeInterval current = it.next();
        if (current.uses.isEmpty()) {
          // These will not have a register assigned later.
          toMerge.add(current);
        } else {
          // So there are uses and possibly a register assigned.
          coalesceAndAddUnassignedSplit(splits, toMerge);
          splits.add(current);
        }
      }
      coalesceAndAddUnassignedSplit(splits, toMerge);
      System.out.println("oldSplits = " + oldSplits);
      System.out.println("splits = " + splits);
    }
  }

  private void coalesceAndAddUnassignedSplit(
      List<LifetimeInterval> splits, List<LifetimeInterval> toMerge) {
    if (!toMerge.isEmpty()) {
      // First we merge all consecutive intervals without uses into a new one without
      // an assigned register.
      LifetimeInterval merged = coalesceIntervals(toMerge);
      assert merged.uses.isEmpty();
      splits.add(merged);
      allocation.remove(merged);
      toMerge.clear();
    }
  }

  private void optimizeSplitPositions() {
    for (List<LifetimeInterval> splits : splitLifetimes.values()) {
      // We may always move the start of spilled intervals further to the front.
      // There are two useful heuristics:
      // 1. Move splits out of loops
      // 2. Move splits to block boundaries
      for (int i = 0; i < splits.size() - 1; i++) {
        LifetimeInterval previous = splits.get(i);
        LifetimeInterval current = splits.get(i + 1);
        if (isSpilled(current) && !isSpilled(previous)) {
          BlockPosition lastUse = previous.lastUse();
          assert lastUse != null
              : "The current interval was spilled, so the previous must have a use";
          BlockPosition bestCandidate = current.from();
          // TODO x) Needs some elaborate guess work (or bloat in CodeBlock) to identify loop
          // nesting levels
        }
      }
    }
  }

  private boolean isSpilled(LifetimeInterval current) {
    return allocation.get(current) == null;
  }

  public static AllocationResult allocateRegisters(LifetimeAnalysisResult lifetimes) {
    return allocateRegisters(lifetimes, AMD64Register.ALLOCATABLE);
  }

  public static AllocationResult allocateRegisters(
      LifetimeAnalysisResult lifetimes, Set<AMD64Register> allocatable) {
    return new LinearScanRegisterAllocator(lifetimes, allocatable).allocate();
  }
}
