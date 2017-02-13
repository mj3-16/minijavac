package minijava.ir.assembler.allocation;

import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.RELOAD;
import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.SPILL;
import static org.jooq.lambda.Seq.seq;

import java.util.*;
import minijava.ir.assembler.StackLayout;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.lifetime.LifetimeInterval;
import minijava.ir.assembler.operands.*;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
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
          System.out.println("splits = " + seq(splits).map(s -> s.ranges).toList());
          // otherwise we have to spill within the first interval and reload at the begin of every following.
          Iterator<LifetimeInterval> it = splits.iterator();
          LifetimeInterval first = it.next();
          // Spill immediately after the definition to avoid spilling more often than the value is defined (e.g. not in
          // loops).
          BlockPosition def = first.defAndUses.first();
          assert def.isDef() : "The first interval of a split was not a def";
          events.put(def, new SpillEvent(SPILL, first));
          while (it.hasNext()) {
            LifetimeInterval following = it.next();
            if (allocation.get(following) == null) {
              // We didn't assign a register to this interval, meaning that all instructions operate on memory.
              // We don't (and can't) reload.
              continue;
            }
            BlockPosition use = following.defAndUses.first();
            assert use.isUse() : "A following use wasn't really a use";
            events.put(use, new SpillEvent(RELOAD, following));
          }
          System.out.println(
              "events = "
                  + seq(events)
                      .map(evt -> evt.v1 + " " + evt.v2.kind + " " + evt.v2.interval.register)
                      .toList());
        });
    return events;
  }

  public AMD64Register assignedRegisterAt(Register what, BlockPosition where) {
    return what.match(
        virt -> {
          for (LifetimeInterval li : getInterval(virt)) {
            if (!li.covers(where)) {
              continue;
            }
            return allocation.get(li);
          }

          return null;
        },
        hardware -> hardware);
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

  public Operand hardwareOperandAt(OperandWidth width, Register register, BlockPosition where) {
    AMD64Register physical = assignedRegisterAt(register, where);

    if (physical != null) {
      // There is a physical register assigned at where
      return new RegisterOperand(width, physical);
    }
    // Otherwise we return a reference to the spill location.
    MemoryOperand spill = spillLocation(width, (VirtualRegister) register);
    if (spill == null) {
      throw new AssertionError(
          "There was no register assigned but also no spill slot for " + register + " at " + where);
    }
    return spill;
  }

  public MemoryOperand spillLocation(OperandWidth width, VirtualRegister register) {
    int offset = (spillSlots.get(register) + 1) * StackLayout.BYTES_PER_STACK_SLOT;
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
