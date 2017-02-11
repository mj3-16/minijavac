package minijava.ir.assembler.deconstruction;

import static com.google.common.collect.Lists.newArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.assembler.allocation.AllocationResult;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.instructions.*;

public class SsaDeconstruction {
  private final List<CodeBlock> linearization;
  private final AllocationResult allocationResult;
  private final Map<CodeBlock, List<Instruction>> resolvedBlocks = new HashMap<>();

  public SsaDeconstruction(List<CodeBlock> linearization, AllocationResult allocationResult) {
    this.linearization = linearization;
    this.allocationResult = allocationResult;
  }

  private List<Instruction> assembleInstructionList() {
    lowerVirtualOperandsAndInsertSpills();
    resolvePhisAndSplitIntervals();
    return flattenIntoInstructionList();
  }

  private void lowerVirtualOperandsAndInsertSpills() {}

  private void resolvePhisAndSplitIntervals() {}

  private List<Instruction> flattenIntoInstructionList() {
    List<Instruction> instructions = new ArrayList<>();
    for (int i = 0; i < linearization.size(); i++) {
      CodeBlock block = linearization.get(i);
      CodeBlock next = i + 1 < linearization.size() ? linearization.get(i + 1) : null;
      instructions.add(new Label(block.label));
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
