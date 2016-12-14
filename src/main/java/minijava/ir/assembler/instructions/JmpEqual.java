package minijava.ir.assembler.instructions;

import minijava.ir.assembler.block.CodeBlock;

/**
 * A conditional jump that is taken if the previous {@see Cmp} instruction found that its first
 * argument equals its second.
 */
public class JmpEqual extends Jmp {

  public JmpEqual(CodeBlock nextBlock) {
    super(nextBlock);
  }

  @Override
  public Type getType() {
    return Type.JMP_EQUAL;
  }
}
