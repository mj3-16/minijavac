package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;

/** An unconditional jump to a new block */
public class Jmp extends Instruction {

  public final CodeBlock nextBlock;

  public Jmp(CodeBlock nextBlock) {
    super(OperandWidth.Quad);
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
  public List<Operand> getArguments() {
    return ImmutableList.of();
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
