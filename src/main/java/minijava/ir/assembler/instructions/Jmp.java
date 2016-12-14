package minijava.ir.assembler.instructions;

import minijava.ir.assembler.block.CodeBlock;

/** An unconditional jump to a new block */
public class Jmp extends Instruction {

  public final CodeBlock nextBlock;

  public Jmp(CodeBlock nextBlock) {
    this.nextBlock = nextBlock;
  }

  @Override
  public String toGNUAssembler() {
    return super.toGNUAssembler() + String.format("\tjmp %s\n", nextBlock.label);
  }

  @Override
  public Type getType() {
    return Type.JMP;
  }
}
