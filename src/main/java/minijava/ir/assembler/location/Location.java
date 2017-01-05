package minijava.ir.assembler.location;

import minijava.ir.assembler.instructions.Argument;

/** Location for an assembler instruction operand (and therefore an intermediate result) */
public abstract class Location implements Argument {

  private String comment;

  protected String formatComment() {
    if (comment != null) {
      return String.format("/*%s*/", comment);
    }
    return "";
  }

  public Location setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public Location setComment(firm.nodes.Node node) {
    return setComment(node.toString());
  }
}
