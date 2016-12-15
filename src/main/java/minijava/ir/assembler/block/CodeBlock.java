package minijava.ir.assembler.block;

import static com.google.common.base.Preconditions.checkArgument;

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
public class CodeBlock implements GNUAssemblerConvertible, Collection<Instruction> {

  public final String label;
  private final List<Instruction> normalInstructions;
  /** They have to be located between the jmp and cmp like instructions */
  private final List<Instruction> cmpOrJmpSupportInstructions;

  private Optional<Cmp> compareInstruction;
  private List<Jmp> conditionalJumps;
  private Optional<Jmp> unconditionalJump;

  public CodeBlock(String label) {
    this.label = label;
    this.normalInstructions = new ArrayList<>();
    this.cmpOrJmpSupportInstructions = new ArrayList<>();
    this.compareInstruction = Optional.empty();
    this.conditionalJumps = new ArrayList<>();
    this.unconditionalJump = Optional.empty();
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    builder.append(label).append(":").append(System.lineSeparator());
    builder.append(
        stream()
            .map(Instruction::toGNUAssembler)
            .collect(Collectors.joining(System.lineSeparator())));
    return builder.toString();
  }

  public int size() {
    return normalInstructions.size()
        + cmpOrJmpSupportInstructions.size()
        + (compareInstruction.isPresent() ? 1 : 0)
        + conditionalJumps.size()
        + (unconditionalJump.isPresent() ? 1 : 0);
  }

  @Override
  public boolean isEmpty() {
    return size() > 0;
  }

  @Override
  public boolean contains(Object o) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Iterator<Instruction> iterator() {
    List<Instruction> others = new ArrayList<>();
    compareInstruction.ifPresent(others::add);
    others.addAll(conditionalJumps);
    unconditionalJump.ifPresent(others::add);
    return Iterators.concat(
        normalInstructions.iterator(), cmpOrJmpSupportInstructions.iterator(), others.iterator());
  }

  @NotNull
  @Override
  public Object[] toArray() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    throw new UnsupportedOperationException();
  }

  /**
   * Add the instruction to the normal instructions
   *
   * <p>jmp or cmp like instructions aren't supported
   */
  @Override
  public boolean add(Instruction instruction) {
    checkArgument(!instruction.isJmpOrCmpLike(), instruction);
    return normalInstructions.add(instruction);
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
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

  public boolean addAll(@NotNull Collection<? extends Instruction> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
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

  public void addConditionalJump(Jmp jmp) {
    checkArgument(jmp.getType() != Instruction.Type.JMP, jmp);
    conditionalJumps.add(jmp);
  }

  public List<Jmp> getConditionalJumps() {
    return Collections.unmodifiableList(conditionalJumps);
  }
}