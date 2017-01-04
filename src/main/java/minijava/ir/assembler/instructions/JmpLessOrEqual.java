package minijava.ir.assembler.instructions;

import minijava.ir.assembler.block.CodeBlock;

/**
 * A conditional jump that is taken if the previous {@see Cmp} instruction found that its first
 * argument is lower than its second or equals it.
 */
public class JmpLessOrEqual extends Jmp {

  public JmpLessOrEqual(CodeBlock nextBlock) {
    super(nextBlock);
  }

  @Override
  public Type getType() {
    return Type.JMP_LESS_OR_EQUAL;
  }
}
