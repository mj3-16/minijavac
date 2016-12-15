package minijava.ir.assembler.block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.instructions.Instruction;
import org.jetbrains.annotations.NotNull;

/**
 * A list of assembler instructions with a label. It is automatically sorted in a way that <code>jmp
 * </code> and <code>cmp</code> like instructions are placed at the end (even if new instructions
 * are inserted at the end)
 */
public class CodeBlock implements GNUAssemblerConvertible, Collection<Instruction> {

  public final String label;
  private final List<Instruction> instructions;
  private int indexOfFirstCmpSupportInstruction;
  private int indexOfFirstJmpInstruction;

  public CodeBlock(String label) {
    this.label = label;
    this.instructions = new ArrayList<>();
    indexOfFirstJmpInstruction = 0;
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    builder.append(label).append(":").append(System.lineSeparator());
    builder.append(
        instructions
            .stream()
            .map(Instruction::toGNUAssembler)
            .collect(Collectors.joining(System.lineSeparator())));
    return builder.toString();
  }

  @Override
  public int size() {
    return instructions.size();
  }

  @Override
  public boolean isEmpty() {
    return instructions.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return instructions.contains(o);
  }

  @NotNull
  @Override
  public Iterator<Instruction> iterator() {
    return instructions.iterator();
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return instructions.toArray();
  }

  @NotNull
  @Override
  public <T> T[] toArray(@NotNull T[] a) {
    return instructions.<T>toArray(a);
  }

  /**
   * Adds the given instruction to the end of this code blocks but keeps the invariant, that <code>
   * jmp</code> and <code>cmp</code> like instructions have to be placed at the very end of a block.
   */
  @Override
  public boolean add(Instruction instruction) {
    if (instruction.isJmpOrCmpLike()) { // insert jumps and compares at the end
      if (instruction.getType().category == Instruction.Category.CMP) {
        if (hasCompareInstruction()) {
          // ignore the instruction as we already have a compare instruction
          return false;
        } else {
          instructions.add(indexOfFirstJmpInstruction, instruction);
          return true;
        }
      } else if (instruction.getType() == Instruction.Type.JMP) {
        if (hasUnconditionalJump()) {
          // ignore the instruction as we already have an unconditional jump
          return false;
        } else {
          return instructions.add(instruction);
        }
      } else {
        if (hasCompareInstruction()) {
          instructions.add(indexOfFirstJmpInstruction + 1, instruction);
        } else {
          instructions.add(indexOfFirstJmpInstruction, instruction);
        }
        return true;
      }
    } else {
      instructions.add(indexOfFirstJmpInstruction, instruction);
      indexOfFirstCmpSupportInstruction++;
      indexOfFirstJmpInstruction++;
      return true;
    }
  }

  /** Use with care! */
  public void addToCmpSupportArea(Instruction instruction) {
    instructions.add(indexOfFirstJmpInstruction, instruction);
    indexOfFirstJmpInstruction++;
  }

  @Override
  public boolean remove(Object o) {
    return instructions.remove(o);
  }

  @Override
  public boolean containsAll(@NotNull Collection<?> c) {
    return instructions.containsAll(c);
  }

  @Override
  public boolean addAll(@NotNull Collection<? extends Instruction> c) {
    for (Instruction instruction : c) {
      add(instruction);
    }
    return c.size() > 0;
  }

  @Override
  public boolean removeAll(@NotNull Collection<?> c) {
    return instructions.removeAll(c);
  }

  @Override
  public boolean retainAll(@NotNull Collection<?> c) {
    return instructions.retainAll(c);
  }

  @Override
  public void clear() {
    instructions.clear();
  }

  public void replaceAll(Instruction oldInstr, Instruction newInstr) {
    instructions.replaceAll(instr -> instr == oldInstr ? newInstr : instr);
  }

  public void prepend(Instruction instruction) {
    if (instruction.isJmpOrCmpLike()) {
      throw new UnsupportedOperationException();
    }
    instructions.add(0, instruction);
  }

  public boolean hasCompareInstruction() {
    for (int i = indexOfFirstJmpInstruction; i < size(); i++) {
      if (instructions.get(i).getType().category == Instruction.Category.CMP) {
        return true;
      }
    }
    return false;
  }

  public boolean hasUnconditionalJump() {
    for (int i = indexOfFirstJmpInstruction; i < size(); i++) {
      if (instructions.get(i).getType() == Instruction.Type.JMP) {
        return true;
      }
    }
    return false;
  }
}
