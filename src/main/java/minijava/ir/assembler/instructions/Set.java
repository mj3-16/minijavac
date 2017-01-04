package minijava.ir.assembler.instructions;

import firm.Relation;
import minijava.ir.assembler.location.Location;

public class Set extends Instruction {

  public final Relation relation;
  public final Location destination;

  public Set(Relation relation, Location destination) {
    this.relation = relation;
    this.destination = destination;
  }

  @Override
  protected String toGNUAssemblerWoComments() {
    String asm = getType().asm;
    switch (relation) {
      case Greater:
        asm += "g";
        break;
      case GreaterEqual:
        asm += "ge";
        break;
      case Less:
        asm += "l";
        break;
      case LessEqual:
        asm += "le";
        break;
      case Equal:
        asm += "e";
        break;
    }
    return asm + " " + destination.toGNUAssembler();
  }

  @Override
  public Type getType() {
    return Type.SET;
  }
}
