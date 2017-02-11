package minijava.ir.assembler.deconstruction;

import static com.google.common.collect.Lists.newArrayList;
import static org.jooq.lambda.Seq.seq;

import java.util.*;
import minijava.ir.assembler.allocation.AllocationResult;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.lifetime.LifetimeInterval;

public class SsaDeconstruction {
  private final List<CodeBlock> linearization;
  private final AllocationResult allocationResult;
  private final Map<CodeBlock, List<Instruction>> resolvedBlocks = new HashMap<>();

  // For caching; O(V) instead of O(E)
  private final Map<CodeBlock, List<LifetimeInterval>> liveAtBegin = new HashMap<>();

  public SsaDeconstruction(List<CodeBlock> linearization, AllocationResult allocationResult) {
    this.linearization = linearization;
    this.allocationResult = allocationResult;
  }

  private List<Instruction> assembleInstructionList() {
    lowerVirtualOperandsAndInsertSpills();
    resolvePhisAndSplitIntervals();
    return flattenIntoInstructionList();
  }

  private void lowerVirtualOperandsAndInsertSpills() {
    for (CodeBlock block : linearization) {
      resolvedBlocks.put(block, InstructionListLowerer.lowerBlock(block, allocationResult));
    }
  }

  private void resolvePhisAndSplitIntervals() {
    // For each control flow edge...
    for (CodeBlock pred : linearization) {
      Set<CodeBlock> successors = pred.exit.getSuccessors();
      boolean predHasMultipleSuccs = successors.size() > 1;
      for (CodeBlock succ : successors) {
        boolean succHasPhis = succ.phis.size() > 0;
        // This check could be more precise if we stored the Preds in the CodeBlock, which we don't.
        // Critical edges without Phis might still slip through, causing trouble for split intervals.
        assert !succHasPhis || predHasMultipleSuccs : "Found a critical edge";
        resolveControlFlowEdge(pred, succ);
      }
    }
  }

  private List<LifetimeInterval> liveAtBegin(CodeBlock succ) {
    return liveAtBegin.computeIfAbsent(
        succ, k -> allocationResult.liveIntervalsAt(new BlockPosition(k, 0)));
  }

  private void resolveControlFlowEdge(CodeBlock pred, CodeBlock succ) {
    // For each interval live at the begin of succ
    for (LifetimeInterval li : liveAtBegin(succ)) {
      boolean startsAtSucc = li.defAndUses.first().block.equals(succ);
      if (startsAtSucc) {
        // li is the interval of a Phi of succ. We don't go through the virtual Phis of succ, but to the allocated
        // Phis at the Label instruction (which always is the first instruction of a block).
        Label label = (Label) resolvedBlocks.get(succ).get(0);
        PhiFunction def =
            seq(label.physicalPhis).filter(phi -> phi.output.equals(li.register)).findFirst().get();
      }
    }
  }

  private List<Instruction> flattenIntoInstructionList() {
    List<Instruction> instructions = new ArrayList<>();
    for (int i = 0; i < linearization.size(); i++) {
      CodeBlock block = linearization.get(i);
      CodeBlock next = i + 1 < linearization.size() ? linearization.get(i + 1) : null;
      instructions.addAll(resolvedBlocks.get(block));
      instructions.addAll(lowerBlockExit(block, next));
    }
    return instructions;
  }

  /**
   * This is a little more complicated than it needs to be, because we try to fall through to
   * successor blocks if possible.
   */
  private ArrayList<Instruction> lowerBlockExit(CodeBlock block, CodeBlock next) {
    return block.exit.match(
        zero -> newArrayList(new Ret()),
        one ->
            one.target.equals(next)
                ? newArrayList() // No jmp needed, just fall through
                : newArrayList(new Jmp(one.target.label)),
        two ->
            two.falseTarget.equals(next)
                ? newArrayList(
                    new Jcc(two.trueTarget.label, two.relation)) // We can fall through to false
                : two.trueTarget.equals(next)
                    ? newArrayList(
                        new Jcc(
                            two.falseTarget.label, two.relation.inversed())) // fall through to true
                    : newArrayList(
                        new Jcc(two.trueTarget.label, two.relation),
                        new Jmp(two.falseTarget.label)));
  }

  public static List<Instruction> assembleInstructionList(
      List<CodeBlock> linearization, AllocationResult allocationResult) {
    return new SsaDeconstruction(linearization, allocationResult).assembleInstructionList();
  }
}
