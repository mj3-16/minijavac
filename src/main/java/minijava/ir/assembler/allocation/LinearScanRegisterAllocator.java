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
    System.out.println(unhandled);
    for (LifetimeInterval current : unhandled) {
      System.out.println();
      System.out.println(current);
      CodeBlock first = current.firstBlock();
      BlockPosition startPosition = current.getLifetimeInBlock(first).fromPosition();

      moveHandledAndInactiveFromActive(startPosition);
      moveHandledAndActiveFromInactive(startPosition);
      System.out.println("active = " + registers(active));
      System.out.println("inactive = " + registers(inactive));
      System.out.println();
      if (!tryAllocateFreeRegister(current)) {
        // Allocation failed
        allocateBlockedRegister(current);
      }
    }

    return new AllocationResult(allocation, splitLifetimes, spillSlotAllocator.spillSlots);
  }

  private List<VirtualRegister> registers(List<LifetimeInterval> intervals) {
    return seq(intervals).map(li -> li.register).toList();
  }

  private boolean tryAllocateFreeRegister(LifetimeInterval current) {
    BlockPosition definition = startOf(current);
    System.out.println("definition = " + definition);
    BlockPosition lastUsage = lastUsage(current);
    Map<AMD64Register, BlockPosition> freeUntil = new TreeMap<>();

    for (AMD64Register register : AMD64Register.allocatable) {
      FixedInterval fixed = fixedIntervals.get(register);
      BlockPosition blockedAt = fixed.ranges.firstIntersectionWith(current.ranges);
      if (blockedAt == null) {
        // We freely choose a position after the last usage
        // We don't use BlockPosition.endOf here, because that might coincide with uses of successor phis.
        blockedAt = BlockPosition.endOf(current.lastBlock());
        // We need to offset further because there might be uses of successor phis
        blockedAt = new BlockPosition(blockedAt.block, blockedAt.pos + 1);
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
    System.out.println(bestCandidate + " for " + current.register);
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
    LifetimeInterval before = splitAndSuspendAfterHalf(current, spillBefore);
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
      if (!AMD64Register.allocatable.contains(locked)) {
        continue;
      }
      BlockPosition blocked = nextBlocked.get(locked);
      boolean goodEnough = current.endsBefore(blocked);
      boolean notWorseThanBest = bestCandidate.v2.equals(blocked);
      if (goodEnough || notWorseThanBest) {
        // locked is a better candidate
        return tuple(locked, blocked);
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
    BlockPosition start = startOf(current);
    System.out.println("start = " + start);

    Map<AMD64Register, BlockPosition> nextUse = new HashMap<>();
    for (AMD64Register register : AMD64Register.allocatable) {
      nextUse.put(register, BlockPosition.endOf(current.lastBlock()));
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
        // No further uses, but it conflicts! So we assume the end of the last block as the next usage.
        nextUseAfterCurrentDef = BlockPosition.endOf(interval.lastBlock());
      }

      BlockPosition oldNext = nextUse.get(register);
      if (oldNext == null || oldNext.compareTo(nextUseAfterCurrentDef) > 0) {
        nextUse.put(register, nextUseAfterCurrentDef);
      }
    }

    System.out.println("nextUse = " + nextUse);
    Tuple2<AMD64Register, BlockPosition> bestCandidate = determineBestCandidate(nextUse, current);
    System.out.println("bestCandidate = " + bestCandidate);
    AMD64Register assignedRegister = bestCandidate.v1;
    BlockPosition farthestNextUse = bestCandidate.v2;

    // nextUsage might be null if start was the last usage (which can only happen if current was split)
    BlockPosition nextUsage = nextUsageAfter(current, start);
    if (false && nextUsage == null) {
      // TODO: We don't do this yet, because we can't say if the next use needs a RegisterOperand or not.
      // This happens when current is the result of the split. Otherwise we should always have a definition and at least
      // one use.
      assert current.defAndUses.first().isUse() : "Assumed the register was split";
      // When this interval was split, it was also assigned a spill slot.
      // By not assigning any register, we can make the lowering step use a MemoryOperand for the use instead.
    } else if (false && nextUsage.compareTo(farthestNextUse) > 0) {
      // TODO: We don't do this yet, because we can't say if the next use needs a RegisterOperand or not.
      // first usage is after any other conflicting interval's next usage.
      // current is to be spilled immediately after its definition.
      // Note that it's crucial that we use a MemoryOperand instruction when spilling, because
      // we don't have enough registers.
      // Actually we should split before the next use which needs a RegisterOperand.
      LifetimeInterval before = splitAndSuspendAfterHalf(current, nextUsage);
      // This will have assigned a spill slot, but the before part is not present anywhere in our data structures.
      // We don't assign it a register, but we still have to note that it's part of the splits.
      getLifetimeIntervals(before.register).add(before);
    } else if (start.equals(farthestNextUse)) {
      // Note that it holds that start = farthestNextUse => nextUsage > farthestNextUse.
      // We need this fallback for massive numbers of Phis, so that all hardware registers are needed simultaneously.
      // If I ever come around to fixing those TODOs above, this shouldn't be needed any more.
      // We try to split at the next usage, where we try to assign a register again.
      if (nextUsage != null) {
        current = splitAndSuspendAfterHalf(current, nextUsage);
        // For the remaining interval, we have to do everything with MemoryOperands.
      }
      spillSlotAllocator.allocateSpillSlot(current.register);
      getLifetimeIntervals(current.register).add(current);
    } else {
      // spill intervals that block the assignedRegister
      // First we split the active interval for assignedRegister.
      // This will delete the unsplit interval and instead re-add the first split part, assigned
      // to the old register, but will re-insert the other conflicting split half into unhandled.
      for (LifetimeInterval interval : filterByAllocatedRegister(active, assignedRegister)) {
        System.out.println("Splitting " + interval.register + " at " + farthestNextUse);
        LifetimeInterval before = splitAndSuspendAfterHalf(interval, farthestNextUse);
        renameInterval(interval, before);
      }

      for (LifetimeInterval interval : filterByAllocatedRegister(inactive, assignedRegister)) {
        // We have to do the same for inactive intervals with the same assigned register.
        // If this intersects at some point with the current register, we have to split it and
        // re-insert the second half into unhandled.
        BlockPosition endOfLifetimeHole = current.ranges.firstIntersectionWith(interval.ranges);
        if (endOfLifetimeHole == null) {
          continue;
        }
        LifetimeInterval before = splitAndSuspendAfterHalf(interval, endOfLifetimeHole);
        renameInterval(interval, before);
      }

      // This will swap out all other intervals currently active for assignedRegister.
      assignRegister(current, assignedRegister);

      FixedInterval fixed = fixedIntervals.get(assignedRegister);
      if (fixed != null) {
        BlockPosition constraintPosition = fixed.ranges.firstIntersectionWith(current.ranges);
        if (constraintPosition != null) {
          // A register constrained kicks in at constraintPosition, so we have to split current (again).
          splitAndSuspendAfterHalf(current, constraintPosition);
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
  private LifetimeInterval splitAndSuspendAfterHalf(
      LifetimeInterval current, BlockPosition splitPos) {
    spillSlotAllocator.allocateSpillSlot(current.register);
    Split<LifetimeInterval> split = current.splitBefore(splitPos);
    if (!split.after.defAndUses.isEmpty()) {
      // Consider the case where we split an interval used in the loop header (e.g. the n in `i < n`) that we
      // chose to split in the body. The after part will have no uses, although it is somewhat alive, a zombie.
      // we discard these and handle them when resolving phis, because we have to spill them and move their value
      // back into its register right before jump back to the header.
      unhandled.add(split.after);
    }
    return split.before;
  }

  private static BlockPosition startOf(LifetimeInterval interval) {
    return interval.ranges.from();
  }

  private static BlockPosition nextUsageAfter(LifetimeInterval interval, BlockPosition after) {
    NavigableSet<BlockPosition> tail = interval.defAndUses.tailSet(after, false);
    return tail.isEmpty() ? null : tail.first();
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
