package minijava.ir.assembler.instructions;

import minijava.ir.assembler.block.CodeBlock;

/**
 * A conditional jump that is taken if the previous {@see Cmp} instruction found that its first
 * argument is lower than its second.
 */
public class JmpLess extends Jmp {

  public JmpLess(CodeBlock nextBlock) {
    super(nextBlock);
  }

  @Override
  public Type getType() {
    return Type.JMP_LESS;
  }
}
