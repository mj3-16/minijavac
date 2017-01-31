package minijava.ir.assembler.instructions;

import com.google.common.collect.ImmutableList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;

/** Moves the source value into the destination */
public class Mov extends Instruction {

  public final Operand source;
  public final Operand destination;

  public Mov(Operand source, Operand destination) {
    super(source.width);
    assert source.width == destination.width;
    this.source = source;
    this.destination = destination;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return createGNUAssemblerWoComments(source, destination);
  }

  @Override
  public Type getType() {
    return Type.MOV;
  }

  @Override
  public List<Operand> getArguments() {
    return ImmutableList.of(source, destination);
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
