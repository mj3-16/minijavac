package minijava.ir.assembler.instructions;

import static minijava.ir.utils.FirmUtils.relationToInstructionSuffix;

import firm.Relation;
import minijava.ir.assembler.block.CodeBlock;

/** Conditional jmp (je, …) */
public class ConditionalJmp extends Jmp {

  public final Relation relation;

  public ConditionalJmp(CodeBlock nextBlock, Relation relation) {
    super(nextBlock);
    this.relation = relation;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return String.format("j%s %s", relationToInstructionSuffix(relation), nextBlock.label);
  }

  @Override
  public Type getType() {
    return Type.COND_JMP;
  }
}
