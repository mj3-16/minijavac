package minijava.ir.assembler.instructions;

import minijava.ir.assembler.block.CodeBlock;

/**
 * A conditional jump that is taken if the previous {@see Cmp} instruction found that its first
 * argument is greater than its second.
 */
public class JmpGreater extends Jmp {

  public JmpGreater(CodeBlock nextBlock) {
    super(nextBlock);
  }

  @Override
  public Type getType() {
    return Type.JMP_GREATER;
  }
}
