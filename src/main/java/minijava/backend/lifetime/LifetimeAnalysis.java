package minijava.backend.lifetime;

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.PhiFunction;
import minijava.backend.instructions.Instruction;
import minijava.backend.operands.Operand;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.operands.Use;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.Register;
import minijava.backend.registers.VirtualRegister;

public class LifetimeAnalysis {
  private final Map<CodeBlock, Set<VirtualRegister>> liveIn = new HashMap<>();
  private final Map<VirtualRegister, LifetimeInterval> intervals = new HashMap<>();
  private final Map<AMD64Register, FixedInterval> fixedIntervals = new HashMap<>();
  private final List<CodeBlock> linearization;

  public LifetimeAnalysis(List<CodeBlock> linearization) {
    this.linearization = linearization;
  }

  public LifetimeAnalysisResult analyse() {
    for (AMD64Register allocatable : AMD64Register.ALLOCATABLE) {
      fixedIntervals.put(allocatable, new FixedInterval(allocatable));
    }

    for (CodeBlock block : Lists.reverse(linearization)) {
      Set<VirtualRegister> live = new HashSet<>();
      addLiveInFromSuccessors(block, live);
      makeAliveInWholeBlock(block, live);
      walkInstructionsBackwards(block, live);
      handleBackEdges(block, live);
      liveIn.put(block, live);
    }
    return new LifetimeAnalysisResult(intervals, fixedIntervals);
  }

  private void handleBackEdges(CodeBlock loopHeader, Set<VirtualRegister> live) {
    // For back-edges we have to be conservative:
    // Every register in live is alive for the complete loop.
    for (CodeBlock partOfLoop : loopHeader.associatedLoopBody) {
      for (VirtualRegister alive : live) {
        getInterval(alive).makeAliveInWholeBlock(partOfLoop);
      }
    }
  }

  private void walkInstructionsBackwards(CodeBlock block, Set<VirtualRegister> live) {
    for (int i = block.instructions.size() - 1; i >= 0; --i) {
      Instruction instruction = block.instructions.get(i);
      BlockPosition def = BlockPosition.definedBy(block, i);
      BlockPosition use = BlockPosition.usedBy(block, i);
      for (Use definition : instruction.defs()) {
        definition.register.match(
            vr -> {
              LifetimeInterval interval = getInterval(vr);
              interval.setDef(def, definition);
              Set<Register> hints = instruction.registerHints();
              if (hints.contains(vr)) {
                addTransitiveHints(vr, interval.fromHints, hints);
              }
              live.remove(vr);
            },
            hr -> {
              if (AMD64Register.ALLOCATABLE.contains(hr)) {
                getFixedInterval(hr).addDef(def);
              }
            });
      }

      for (Use usage : instruction.uses()) {
        usage.register.match(
            vr -> {
              LifetimeInterval interval = getInterval(vr);
              interval.addUse(use, usage);
              Set<Register> hints = instruction.registerHints();
              if (live.add(vr) && hints.contains(vr)) {
                // This was the last usage.
                addTransitiveHints(vr, interval.toHints, hints);
              }
            },
            hr -> {
              if (AMD64Register.ALLOCATABLE.contains(hr)) {
                getFixedInterval(hr).addUse(use);
              }
            });
      }
    }

    for (PhiFunction phi : block.phis) {
      // N.B.: phi output registers aren't visible before the begin of the block, as aren't inputs.
      Use def = phi.output.writes(true);
      if (def != null && live.remove(def.register)) {
        VirtualRegister vr = (VirtualRegister) def.register;
        LifetimeInterval interval = getInterval(vr);
        interval.setDef(BlockPosition.beginOf(block), def);
        addTransitiveHints(vr, interval.fromHints, phi.registerHints());
      }
    }
  }

  private FixedInterval getFixedInterval(AMD64Register hr) {
    return fixedIntervals.get(hr);
  }

  private void makeAliveInWholeBlock(CodeBlock block, Set<VirtualRegister> live) {
    for (VirtualRegister alive : live) {
      // Conservatively assume alive in the whole block at first
      LifetimeInterval interval = getInterval(alive);
      interval.makeAliveInWholeBlock(block);
      intervals.put(alive, interval);
    }
  }

  private void addLiveInFromSuccessors(CodeBlock block, Set<VirtualRegister> live) {
    for (CodeBlock successor : block.exit.getSuccessors()) {
      Set<VirtualRegister> successorLiveIn =
          liveIn.computeIfAbsent(successor, k -> new HashSet<>());
      for (VirtualRegister register : successorLiveIn) {
        live.add(register);
      }
    }

    for (CodeBlock successor : block.exit.getSuccessors()) {
      for (PhiFunction phi : successor.phis) {
        Operand input = phi.inputs.get(block);
        Set<Use> reads = input.reads(false, true);
        reads.addAll(phi.output.reads(true, true));
        for (Use use : reads) {
          if (use.register instanceof VirtualRegister) {
            VirtualRegister aliveVirtual = (VirtualRegister) use.register;
            live.add(aliveVirtual);
            getInterval(aliveVirtual).addUse(BlockPosition.endOf(block), use);
          }
        }

        if (input instanceof RegisterOperand) {
          RegisterOperand op = (RegisterOperand) input;
          if (op.register instanceof VirtualRegister) {
            VirtualRegister inputReg = (VirtualRegister) op.register;
            addTransitiveHints(inputReg, getInterval(inputReg).toHints, phi.registerHints(block));
          }
        }
      }
    }
  }

  private void addTransitiveHints(
      VirtualRegister self, Set<Register> intervalHints, Set<Register> instructionHints) {
    intervalHints.add(self);
    for (Register register : instructionHints) {
      if (register.equals(self)) {
        continue;
      }
      register.match(
          virt -> {
            LifetimeInterval connectedInterval = getInterval(virt);
            intervalHints.addAll(connectedInterval.toHints);
            intervalHints.addAll(connectedInterval.fromHints);
          },
          intervalHints::add);
    }
  }

  private LifetimeInterval getInterval(VirtualRegister alive) {
    return intervals.computeIfAbsent(alive, LifetimeInterval::new);
  }

  public static LifetimeAnalysisResult analyse(List<CodeBlock> linearization) {
    return new LifetimeAnalysis(linearization).analyse();
  }
}
