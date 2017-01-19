package minijava.ir.assembler.block;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterators;
import firm.nodes.Block;
import java.util.*;
import java.util.stream.Collectors;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.instructions.Cmp;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.instructions.Jmp;
import org.jetbrains.annotations.NotNull;

/**
 * A list of assembler instructions with a label, optional cmp support, cmp and jmp instructions.
 */
public class CodeBlock implements GNUAssemblerConvertible, Iterable<Instruction> {

  public static class FollowingBlockInfo {
    public final CodeBlock block;
    /**
     * Number of instructions between the end of the current block (outer block) and the start of
     * this block.
     */
    public final int instructionsBetween;

    public FollowingBlockInfo(CodeBlock block, int instructionsBetween) {
      this.block = block;
      this.instructionsBetween = instructionsBetween;
    }

    @Override
    public String toString() {
      return String.format("(Block %s %s)", block, instructionsBetween);
    }

    @Override
    public int hashCode() {
      return block.hashCode() ^ instructionsBetween;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof FollowingBlockInfo
          && ((FollowingBlockInfo) obj).instructionsBetween == instructionsBetween
          && ((FollowingBlockInfo) obj).block.equals(block);
    }
  }

  public final String label;
  public final Block firmBlock;
  private final List<Instruction> blockStartInstructions;
  private final List<Instruction> normalInstructions;
  private final List<Instruction> phiHelperInstructions;
  /** They have to be located between the jmp and cmp like instructions */
  private final List<Instruction> cmpOrJmpSupportInstructions;

  private Optional<Cmp> compareInstruction;
  private List<Instruction> afterCompareInstructions;
  private List<Jmp> conditionalJumps;
  private Optional<Jmp> unconditionalJump;
  private final List<Instruction> phiBHelperInstructions;
  private final List<Instruction> afterConditionalJumpsInstructions;
  /** Blocks that can follow the current block in an execution. */
  private Set<FollowingBlockInfo> followingBlocks;

  private Map<CodeBlock, Integer> distanceToFollowingBlocks;
  private Set<Argument> argumentsUsedByFollowingBlocks;

  public CodeBlock(String label, Block firmBlock) {
    this.label = label;
    this.firmBlock = firmBlock;
    this.blockStartInstructions = new ArrayList<>();
    this.normalInstructions = new ArrayList<>();
    this.phiHelperInstructions = new ArrayList<>();
    this.cmpOrJmpSupportInstructions = new ArrayList<>();
    this.compareInstruction = Optional.empty();
    this.phiBHelperInstructions = new ArrayList<>();
    this.conditionalJumps = new ArrayList<>();
    this.unconditionalJump = Optional.empty();
    this.afterCompareInstructions = new ArrayList<>();
    this.afterConditionalJumpsInstructions = new ArrayList<>();
    this.followingBlocks = null;
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    builder.append(label).append(":").append(System.lineSeparator());
    builder.append(
        seq(this)
            .map(Instruction::toGNUAssembler)
            .collect(Collectors.joining(System.lineSeparator())));
    return builder.toString();
  }

  public int size() {
    return blockStartInstructions.size()
        + normalInstructions.size()
        + phiHelperInstructions.size()
        + cmpOrJmpSupportInstructions.size()
        + (compareInstruction.isPresent() ? 1 : 0)
        + conditionalJumps.size()
        + (unconditionalJump.isPresent() ? 1 : 0)
        + afterCompareInstructions.size()
        + phiBHelperInstructions.size()
        + afterConditionalJumpsInstructions.size();
  }

  @NotNull
  @Override
  public Iterator<Instruction> iterator() {
    List<Instruction> others = new ArrayList<>();
    compareInstruction.ifPresent(others::add);
    if (hasCompare()) {
      others.addAll(phiBHelperInstructions);
    }
    others.addAll(afterCompareInstructions);
    others.addAll(conditionalJumps);
    others.addAll(afterConditionalJumpsInstructions);
    unconditionalJump.ifPresent(others::add);
    List<Instruction> tmpIt = hasCompare() ? new ArrayList<>() : phiBHelperInstructions;
    return Iterators.concat(
        blockStartInstructions.iterator(),
        normalInstructions.iterator(),
        phiHelperInstructions.iterator(),
        tmpIt.iterator(),
        cmpOrJmpSupportInstructions.iterator(),
        others.iterator());
  }

  /**
   * Add the instruction to the normal instructions
   *
   * <p>jmp or cmp like instructions aren't supported
   */
  public boolean add(Instruction instruction) {
    checkArgument(!instruction.isJmpOrCmpLike(), instruction);
    return normalInstructions.add(instruction);
  }

  /**
   * The instruction will be output between the normal instructions and the cmp instruction (if
   * there's any)
   *
   * <p>Use with care!
   */
  public void addToCmpOrJmpSupportInstructions(Instruction instruction) {
    cmpOrJmpSupportInstructions.add(instruction);
  }

  public void addToAfterCompareInstructions(Instruction instruction) {
    afterCompareInstructions.add(instruction);
  }

  /**
   * Prepends the instructions to the normal instructions
   *
   * <p>jmp or cmp like instructions aren't supported
   */
  public void prepend(Instruction... instructions) {
    for (int i = instructions.length - 1; i >= 0; i--) {
      checkArgument(!instructions[i].isJmpOrCmpLike(), instructions[i]);
      normalInstructions.add(0, instructions[i]);
    }
  }

  public boolean hasCompare() {
    return compareInstruction.isPresent();
  }

  public void setCompare(Cmp cmp) {
    compareInstruction = Optional.of(cmp);
  }

  public void setUnconditionalJump(Jmp jmp) {
    checkArgument(jmp.getType() == Instruction.Type.JMP, jmp);
    unconditionalJump = Optional.of(jmp);
  }

  public void setDefaultUnconditionalJump(Jmp jmp) {
    if (!unconditionalJump.isPresent()) {
      unconditionalJump = Optional.of(jmp);
    }
  }

  public void addConditionalJump(Jmp jmp) {
    checkArgument(jmp.getType() != Instruction.Type.JMP, jmp);
    conditionalJumps.add(jmp);
  }

  public void addPhiHelperInstruction(Instruction instruction) {
    phiHelperInstructions.add(instruction);
  }

  public void addPhiBHelperInstruction(Instruction instruction) {
    phiBHelperInstructions.add(instruction);
  }

  public void addBlockStartInstruction(Instruction instruction) {
    blockStartInstructions.add(instruction);
  }

  public void addAfterConditionalJumpsInstruction(Instruction instruction) {
    afterConditionalJumpsInstructions.add(instruction);
  }

  @Override
  public String toString() {
    return label;
  }

  public List<LinearCodeSegment.InstructionOrString> getAllLines() {
    List<LinearCodeSegment.InstructionOrString> arr = new ArrayList<>();
    arr.add(new LinearCodeSegment.InstructionOrString(label + ":"));
    arr.add(new LinearCodeSegment.InstructionOrString(""));
    seq(this).map(LinearCodeSegment.InstructionOrString::new).forEach(arr::add);
    return arr;
  }

  /**
   * Call this method after the generation of a all code blocks finished to update to initialize the
   * set of following blocks. TODO: improve perfomance (if it matters)
   */
  public void initFollowingBlocks() {
    // we just do a depth first search
    followingBlocks = new HashSet<>();
    distanceToFollowingBlocks = new HashMap<>();
    Stack<FollowingBlockInfo> workList = new Stack<>();
    // we add the current block to the work list
    workList.push(new FollowingBlockInfo(this, 0));
    while (!workList.isEmpty()) {
      FollowingBlockInfo item = workList.pop();
      int distanceOfEndToCurrentBlock = item.instructionsBetween + item.block.size();
      for (CodeBlock adjacentBlock : item.block.getJumpedToBlocks()) {
        if (!distanceToFollowingBlocks.containsKey(adjacentBlock)) {
          FollowingBlockInfo info =
              new FollowingBlockInfo(adjacentBlock, distanceOfEndToCurrentBlock);
          followingBlocks.add(info);
          distanceToFollowingBlocks.put(adjacentBlock, distanceOfEndToCurrentBlock);
          workList.add(info);
        }
      }
    }
    followingBlocks = Collections.unmodifiableSet(followingBlocks);
    distanceToFollowingBlocks = Collections.unmodifiableMap(distanceToFollowingBlocks);
  }

  /**
   * Initializes the set of arguments used by the following blocks. Attention: the {@link
   * CodeBlock::initFollowingBlocks} should be executed before this method.
   */
  public void initArgumentsUsedByFollowingBlocks() {
    assert followingBlocks != null;
    this.argumentsUsedByFollowingBlocks = new HashSet<>();
    for (FollowingBlockInfo followingBlock : followingBlocks) {
      for (Instruction followingInstruction : followingBlock.block) {
        this.argumentsUsedByFollowingBlocks.addAll(followingInstruction.getArguments());
      }
    }
    this.argumentsUsedByFollowingBlocks =
        Collections.unmodifiableSet(argumentsUsedByFollowingBlocks);
  }

  /**
   * Returns a list of blocks that this block might jump to (i.e. all blocks that can directly
   * follow this block in an execution).
   */
  public List<CodeBlock> getJumpedToBlocks() {
    List<CodeBlock> blocks = new ArrayList<>();
    conditionalJumps.forEach(j -> blocks.add(j.nextBlock));
    unconditionalJump.ifPresent(j -> blocks.add(j.nextBlock));
    return Collections.unmodifiableList(blocks);
  }

  public Set<Argument> getArgumentsUsedByFollowingBlocks() {
    return argumentsUsedByFollowingBlocks;
  }
}
