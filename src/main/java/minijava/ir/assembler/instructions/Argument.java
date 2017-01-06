package minijava.ir.assembler.instructions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.block.CodeBlock;

/** Argument for an assembler instruction */
public abstract class Argument implements GNUAssemblerConvertible {

  /**
   * Relations to instructions in a given code block (instructions that use a given Argument
   * instance)
   */
  public static class CodeBlockRelation {

    private Instruction firstInstruction;
    private Instruction lastInstruction;
    private List<Instruction> instructions = new ArrayList<>();

    public void addRelation(Instruction instruction) {
      if (firstInstruction == null
          || instruction.getNumberInBlock() < firstInstruction.getNumberInBlock()) {
        firstInstruction = instruction;
      }
      if (lastInstruction == null
          || instruction.getNumberInBlock() > lastInstruction.getNumberInBlock()) {
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
  }

  protected final Map<CodeBlock, CodeBlockRelation> codeBlockRelations = new HashMap<>();

  public CodeBlockRelation getCodeBlockRelation(CodeBlock block) {
    if (!codeBlockRelations.containsKey(block)) {
      codeBlockRelations.put(block, new CodeBlockRelation());
    }
    return codeBlockRelations.get(block);
  }

  public void addUsedByRelation(Instruction instruction) {
    getCodeBlockRelation(instruction.getParentBlock()).addRelation(instruction);
  }
}
