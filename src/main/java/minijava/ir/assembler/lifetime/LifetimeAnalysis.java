package minijava.ir.assembler.lifetime;

import com.google.common.collect.BiMap;
import com.google.common.collect.Lists;
import firm.nodes.Block;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.utils.NodeUtils;

public class LifetimeAnalysis {
  private final Map<CodeBlock, Set<VirtualRegister>> liveIn = new HashMap<>();
  private final Map<VirtualRegister, LifetimeInterval> intervals = new HashMap<>();
  private final Map<AMD64Register, FixedInterval> fixedIntervals = new HashMap<>();
  private final BiMap<Block, CodeBlock> blocks;
  private final List<CodeBlock> linearization;

  public LifetimeAnalysis(BiMap<Block, CodeBlock> blocks, List<CodeBlock> linearization) {
    this.blocks = blocks;
    this.linearization = linearization;
  }

  public LifetimeAnalysisResult analyse() {
    for (CodeBlock block : Lists.reverse(linearization)) {
      Block irBlock = blocks.inverse().get(block);
      System.out.println("irBlock = " + irBlock);
      Set<VirtualRegister> live = new HashSet<>();

      addLiveInFromSuccessors(block, live);

      System.out.println("live at block end = " + live);

      makeAliveInWholeBlock(block, live);

      walkInstructionsBackwards(block, live);

      handleBackEdges(irBlock, live);

      System.out.println("live at block begin = " + live);

      liveIn.put(block, live);
    }
    return new LifetimeAnalysisResult(new ArrayList<>(intervals.values()), fixedIntervals);
  }

  private void handleBackEdges(Block irBlock, Set<VirtualRegister> live) {
    for (int predNum : NodeUtils.incomingBackEdges(irBlock)) {
      // We have a back edge from pred with predNum. For these we have to be conservative:
      // Every register in live is alive for the complete loop.
      Block irLoopFooter = (Block) irBlock.getPred(predNum).getBlock();
      for (VirtualRegister alive : live) {
        for (Block irLoopBlock : allBlocksBetween(irLoopFooter, irBlock)) {
          getInterval(alive).makeAliveInWholeBlock(blocks.get(irLoopBlock));
        }
      }
    }
  }

  private void walkInstructionsBackwards(CodeBlock block, Set<VirtualRegister> live) {
    for (int i = block.instructions.size() - 1; i >= 0; --i) {
      int idx = i;
      Instruction instruction = block.instructions.get(i);
      for (Register defined : instruction.definitions()) {
        defined.match(
            vr -> {
              getInterval(vr).setDef(block, idx);
              live.remove(defined);
            },
            hr -> {
              getFixedInterval(hr).addDef(block, idx);
            });
      }

      for (Register used : instruction.usages()) {
        used.match(
            vr -> {
              getInterval(vr).addUse(block, idx);
              live.add(vr);
            },
            hr -> {
              getFixedInterval(hr).addUse(block, idx);
            });
      }
    }

    for (PhiFunction phi : block.phis) {
      // N.B.: phi output registers aren't visible before the begin of the block, as aren't inputs.
      live.remove(phi.output);
    }
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
        Register alive = phi.inputs.get(block);
        if (alive instanceof VirtualRegister) {
          live.add((VirtualRegister) alive);
        }
      }
    }
  }

  private Set<Block> allBlocksBetween(Block source, Block target) {
    Set<Block> reachable = new HashSet<>();
    ArrayDeque<Block> toVisit = new ArrayDeque<>();
    toVisit.add(source);
    while (!toVisit.isEmpty()) {
      Block cur = toVisit.removeFirst();
      if (reachable.contains(cur)) {
        continue;
      }
      reachable.add(cur);
      if (!target.equals(source)) {
        NodeUtils.getPredecessorBlocks(cur).forEach(toVisit::add);
      }
    }
    return reachable;
  }

  private LifetimeInterval getInterval(VirtualRegister alive) {
    return intervals.computeIfAbsent(alive, LifetimeInterval::new);
  }

  private FixedInterval getFixedInterval(AMD64Register register) {
    return fixedIntervals.computeIfAbsent(register, FixedInterval::new);
  }

  public static LifetimeAnalysisResult analyse(
      BiMap<Block, CodeBlock> blocks, List<CodeBlock> linearization) {
    return new LifetimeAnalysis(blocks, linearization).analyse();
  }
}
