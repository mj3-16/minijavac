package minijava.ir.assembler.instructions;

import static minijava.ir.utils.FirmUtils.relationToInstructionSuffix;

import firm.Relation;
import minijava.ir.assembler.registers.Register;

public class Set extends UnaryInstruction {

  public final Relation relation;

  public Set(Relation relation, Register destination) {
    super(destination);
    this.relation = relation;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    return String.format(
        "%s%s %s", getType().asm, relationToInstructionSuffix(relation), arg.toGNUAssembler());
  }

  @Override
  public Type getType() {
    return Type.SET;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
