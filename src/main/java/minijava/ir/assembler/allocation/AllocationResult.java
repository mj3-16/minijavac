package minijava.ir.assembler.allocation;

import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.RELOAD;
import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.SPILL;
import static org.jooq.lambda.Seq.seq;

import java.util.*;
import minijava.ir.assembler.StackLayout;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.operands.AddressingMode;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

public class AllocationResult {
  public final Map<LifetimeInterval, AMD64Register> allocation;
  public final Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes;
  public final Map<VirtualRegister, Integer> spillSlots;
  public final TreeMap<BlockPosition, SpillEvent> spillEvents;

  public AllocationResult(
      Map<LifetimeInterval, AMD64Register> allocation,
      Map<VirtualRegister, List<LifetimeInterval>> splitLifetimes,
      Map<VirtualRegister, Integer> spillSlots) {
    this.allocation = allocation;
    this.splitLifetimes = splitLifetimes;
    this.spillSlots = spillSlots;
    this.spillEvents = determineWhereToSpillAndReload();
  }

  private TreeMap<BlockPosition, SpillEvent> determineWhereToSpillAndReload() {
    TreeMap<BlockPosition, SpillEvent> events = new TreeMap<>();
    splitLifetimes.forEach(
        (reg, splits) -> {
          if (splits.size() < 2) {
            // No spilling needed.
            return;
          }
          // otherwise we have to spill within the first interval and reload at the begin of every following.
          Iterator<LifetimeInterval> it = splits.iterator();
          LifetimeInterval first = it.next();
          // Spill immediately after the definition to avoid spilling more often than the value is defined (e.g. not in
          // loops).
          spillEvents.put(first.defAndUses.first(), new SpillEvent(SPILL, first));
          while (it.hasNext()) {
            LifetimeInterval following = it.next();
            if (allocation.get(following) == null) {
              // We didn't assign a register to this interval, meaning that all instructions operate on memory.
              // We don't (and can't) reload.
              continue;
            }
            spillEvents.put(following.defAndUses.first(), new SpillEvent(RELOAD, following));
          }
        });
    return events;
  }

  public AMD64Register assignedRegisterAt(VirtualRegister what, BlockPosition where) {
    for (LifetimeInterval li : getInterval(what)) {
      if (!li.covers(where)) {
        continue;
      }
      return allocation.get(li);
    }

    return null;
  }

  private List<LifetimeInterval> getInterval(VirtualRegister what) {
    List<LifetimeInterval> lis = splitLifetimes.get(what);
    return lis != null ? lis : new ArrayList<>();
  }

  public List<LifetimeInterval> liveIntervalsAt(BlockPosition position) {
    List<LifetimeInterval> live = new ArrayList<>();
    splitLifetimes.forEach(
        (vr, splits) -> {
          seq(splits).filter(li -> li.covers(position)).findFirst().ifPresent(live::add);
        });
    return live;
  }

  public MemoryOperand spillLocation(OperandWidth width, VirtualRegister register) {
    int offset = spillSlots.get(register) * StackLayout.BYTES_PER_STACK_SLOT;
    return new MemoryOperand(width, new AddressingMode(-offset, AMD64Register.BP));
  }

  public static class SpillEvent {
    public final Kind kind;
    public final LifetimeInterval interval;

    private SpillEvent(Kind kind, LifetimeInterval interval) {
      this.kind = kind;
      this.interval = interval;
    }

    public enum Kind {
      SPILL,
      RELOAD
    }
  }
}
