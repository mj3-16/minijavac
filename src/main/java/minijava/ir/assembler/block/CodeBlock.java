package minijava.ir.assembler.block;

import static com.google.common.base.Preconditions.checkArgument;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterators;
import java.util.*;
import java.util.stream.Collectors;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.instructions.Cmp;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.instructions.Jmp;
import org.jetbrains.annotations.NotNull;

/**
 * A list of assembler instructions with a label, optional cmp support, cmp and jmp instructions.
 */
public class CodeBlock implements GNUAssemblerConvertible, Iterable<Instruction> {

  public final String label;
  private final List<Instruction> blockStartInstructions;
  private final List<Instruction> normalInstructions;
  private final List<Instruction> phiHelperInstructions;
  /** They have to be located between the jmp and cmp like instructions */
  private final List<Instruction> cmpOrJmpSupportInstructions;

  private Optional<Cmp> compareInstruction;
  private List<Instruction> afterCompareInstructions;
  private List<Jmp> conditionalJumps;
  private Optional<Jmp> unconditionalJump;

  public CodeBlock(String label) {
    this.label = label;
    this.blockStartInstructions = new ArrayList<>();
    this.normalInstructions = new ArrayList<>();
    this.phiHelperInstructions = new ArrayList<>();
    this.cmpOrJmpSupportInstructions = new ArrayList<>();
    this.compareInstruction = Optional.empty();
    this.conditionalJumps = new ArrayList<>();
    this.unconditionalJump = Optional.empty();
    this.afterCompareInstructions = new ArrayList<>();
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
        + afterCompareInstructions.size();
  }

  @NotNull
  @Override
  public Iterator<Instruction> iterator() {
    List<Instruction> others = new ArrayList<>();
    compareInstruction.ifPresent(others::add);
    others.addAll(afterCompareInstructions);
    others.addAll(conditionalJumps);
    unconditionalJump.ifPresent(others::add);
    return Iterators.concat(
        blockStartInstructions.iterator(),
        normalInstructions.iterator(),
        phiHelperInstructions.iterator(),
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

  public List<Instruction> getAfterCompareInstructions() {
    return Collections.unmodifiableList(afterCompareInstructions);
  }

  public boolean addAll(@NotNull Collection<? extends Instruction> c) {
    throw new UnsupportedOperationException();
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

  public boolean hasUnconditionalJump() {
    return unconditionalJump.isPresent();
  }

  public Optional<Cmp> getCompare() {
    return compareInstruction;
  }

  public Optional<Jmp> getUnconditionalJump() {
    return unconditionalJump;
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

  public List<Jmp> getConditionalJumps() {
    return Collections.unmodifiableList(conditionalJumps);
  }

  public void addBlockStartInstruction(Instruction instruction) {
    blockStartInstructions.add(instruction);
  }

  public List<Instruction> getBlockStartInstructions() {
    return Collections.unmodifiableList(blockStartInstructions);
  }
}
