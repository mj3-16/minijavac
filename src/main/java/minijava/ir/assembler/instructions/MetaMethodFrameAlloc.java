package minijava.ir.assembler.instructions;

import static minijava.ir.assembler.instructions.Instruction.Type.META_FRAME_ALLOC;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;

/** Meta instruction to allocate a frame at the start of a method */
public class MetaMethodFrameAlloc extends Instruction {

  public MetaMethodFrameAlloc() {
    super(OperandWidth.Quad);
  }

  @Override
  public Type getType() {
    return META_FRAME_ALLOC;
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of();
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    throw new RuntimeException();
  }
}
