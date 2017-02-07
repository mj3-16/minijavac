package minijava.ir.assembler.lifetime;

import com.google.common.collect.Lists;
import firm.nodes.Block;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import minijava.ir.utils.NodeUtils;

public class LifetimeAnalysis {
  private final Map<CodeBlock, Set<VirtualRegister>> liveIn = new HashMap<>();
  private final Map<VirtualRegister, LifetimeInterval> intervals = new HashMap<>();
  private final Map<Block, CodeBlock> blocks;
  private final List<Block> linearization;

  public LifetimeAnalysis(Map<Block, CodeBlock> blocks, List<Block> linearization) {
    this.blocks = blocks;
    this.linearization = linearization;
  }

  public Map<VirtualRegister, LifetimeInterval> analyse() {
    for (Block irBlock : Lists.reverse(linearization)) {
      System.out.println("irBlock = " + irBlock);
      CodeBlock block = blocks.get(irBlock);
      Set<VirtualRegister> live = new HashSet<>();

      addLiveInFromSuccessors(block, live);

      System.out.println("live at block end = " + live);

      makeAliveInWholeBlock(block, live);

      walkInstructionsBackwards(block, live);

      handleBackEdges(irBlock, live);

      System.out.println("live at block begin = " + live);

      liveIn.put(block, live);
    }
    return intervals;
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
      Instruction instruction = block.instructions.get(i);
      for (VirtualRegister defined : instruction.definitions()) {
        getInterval(defined).makeAliveFrom(block, i);
        live.remove(defined);
      }

      for (VirtualRegister used : instruction.usages()) {
        getInterval(used).makeAliveUntil(block, i);
        live.add(used);
      }
    }

    for (PhiFunction phi : block.phis) {
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

  public static Map<VirtualRegister, LifetimeInterval> analyse(
      Map<Block, CodeBlock> blocks, List<Block> linearization) {
    return new LifetimeAnalysis(blocks, linearization).analyse();
  }
}
