package minijava.ir.assembler.instructions;

import java.util.*;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.location.Register;

/** Argument for an assembler instruction */
public abstract class Argument implements GNUAssemblerConvertible {

  /**
   * Relations to instructions in a given code block (instructions that use a given Argument
   * instance)
   */
  public static class InstructionRelations {

    private final Argument argument;

    private Map<CodeBlock, List<Instruction>> instructionsInBlocks = new HashMap<>();

    /** The instructions are implicitly sorted ascending by their number in the code block */
    private List<Instruction> instructions = new ArrayList<>();

    public InstructionRelations(Argument argument) {
      this.argument = argument;
    }

    public void addRelation(Instruction instruction) {
      instructions.add(instruction);
      if (!instructionsInBlocks.containsKey(instruction.getParentBlock())) {
        instructionsInBlocks.put(instruction.getParentBlock(), new ArrayList<>());
      }
      instructionsInBlocks.get(instruction.getParentBlock()).add(instruction);
    }

    public boolean isEmpty() {
      return instructions.isEmpty();
    }

    /** Number of code blocks the argument is used in. */
    public int getNumberOfBlockUsages() {
      return instructionsInBlocks.size();
    }

    public boolean isUsedLaterInBlockOfInstruction(Instruction currentInstruction) {
      CodeBlock block = currentInstruction.getParentBlock();
      if (instructionsInBlocks.containsKey(block)) {
        List<Instruction> instructions = instructionsInBlocks.get(block);
        return instructions.get(instructions.size() - 1).getNumberInSegment()
            > currentInstruction.getNumberInSegment();
      }
      return false;
    }

    public boolean isUsedInFollowingBlocks(CodeBlock block) {
      return block.getArgumentsUsedByFollowingBlocks().contains(argument);
    }

    public boolean isUsedAfterInstruction(Instruction instruction) {
      return isUsedLaterInBlockOfInstruction(instruction)
          || isUsedInFollowingBlocks(instruction.getParentBlock());
    }

    /**
     * Returns the next usage of the argument. It returns the current instruction if the current
     * instruction uses the argument.
     */
    public Optional<Instruction> getNextUsageInBlock(Instruction currentInstruction) {
      CodeBlock block = currentInstruction.getParentBlock();
      if (instructionsInBlocks.containsKey(block)) {
        for (Instruction instruction : instructionsInBlocks.get(block)) {
          if (instruction.getNumberInSegment() >= instruction.getNumberInSegment()) {
            return Optional.of(instruction);
          }
        }
      }
      return Optional.empty();
    }
  }

  public final Register.Width width;

  public Argument(Register.Width width) {
    this.width = width;
  }

  public final InstructionRelations instructionRelations = new InstructionRelations(this);

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
