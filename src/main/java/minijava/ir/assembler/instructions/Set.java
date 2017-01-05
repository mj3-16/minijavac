package minijava.ir.assembler.instructions;

import static minijava.ir.utils.FirmUtils.relationToInstructionSuffix;

import firm.Relation;
import minijava.ir.assembler.location.Location;

public class Set extends UnaryInstruction {

  public final Relation relation;

  public Set(Relation relation, Location destination) {
    super(destination);
    this.relation = relation;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    String asm = getType().asm;
    return String.format(
        "%s%s %s", getType().asm, relationToInstructionSuffix(relation), arg.toGNUAssembler());
  }

  @Override
  public Type getType() {
    return Type.SET;
  }
}
