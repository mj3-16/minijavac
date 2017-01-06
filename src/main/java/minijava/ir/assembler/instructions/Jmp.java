package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.block.CodeBlock;

/** An unconditional jump to a new block */
public class Jmp extends Instruction {

  public final CodeBlock nextBlock;

  public Jmp(CodeBlock nextBlock) {
    this.nextBlock = nextBlock;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return super.toGNUAssemblerWoComments() + " " + nextBlock.label;
  }

  @Override
  public Type getType() {
    return Type.JMP;
  }

  @Override
  public List<Argument> getArguments() {
    return ImmutableList.of();
  }
}
