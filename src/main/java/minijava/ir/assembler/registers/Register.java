package minijava.ir.assembler.registers;

/** Register for an assembler register */
public abstract class Register {

  private String comment;

  protected String formatComment() {
    if (comment != null) {
      return String.format("/*%s*/", comment);
    }
    return "";
  }

  public Register setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public Register setComment(firm.nodes.Node node) {
    return setComment(node.toString());
  }
}
