package minijava.ir.assembler.instructions;

import java.util.*;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.location.Register;

/** Argument for an assembler instruction */
public abstract class Argument implements GNUAssemblerConvertible {

  /**
   * Relations to instructions in a given code block (instructions that use a given Argument
   * instance)
   */
  public static class InstructionRelations {

    private Instruction firstInstruction;
    private Instruction lastInstruction;

    /** The instructions are implicitly sorted ascending by their number in the code block */
    private List<Instruction> instructions = new ArrayList<>();

    public void addRelation(Instruction instruction) {
      if (firstInstruction == null
          || instruction.getNumberInSegment() < firstInstruction.getNumberInSegment()) {
        firstInstruction = instruction;
      }
      if (lastInstruction == null
          || instruction.getNumberInSegment() > lastInstruction.getNumberInSegment()) {
        lastInstruction = instruction;
      }
      instructions.add(instruction);
    }

    public Instruction getFirstInstruction() {
      return firstInstruction;
    }

    public Instruction getLastInstruction() {
      return lastInstruction;
    }

    public boolean isEmpty() {
      return instructions.isEmpty();
    }

    public Optional<Instruction> nextUsedInInstruction(Instruction currentInstruction) {
      int curNum = currentInstruction.getNumberInSegment();
      if (isEmpty()
          || curNum <= firstInstruction.getNumberInSegment()
          || curNum >= lastInstruction.getNumberInSegment()) {
        return Optional.empty();
      }
      for (Instruction instruction : instructions) {
        if (instruction.getNumberInSegment() > curNum) {
          return Optional.of(instruction);
        }
      }
      throw new RuntimeException();
    }

    public int getLastInstructionNumber() {
      return lastInstruction.getNumberInSegment();
    }
  }

  public final Register.Width width;

  public Argument(Register.Width width) {
    this.width = width;
  }

  public final InstructionRelations instructionRelations = new InstructionRelations();

  public void addUsedByRelation(Instruction instruction) {
    instructionRelations.addRelation(instruction);
  }

  public String getComment() {
    return "";
  }

  public boolean isUsed() {
    return !instructionRelations.isEmpty();
  }
}
