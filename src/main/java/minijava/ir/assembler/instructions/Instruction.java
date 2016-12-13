package minijava.ir.assembler.instructions;

import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.GNUAssemblerConvertible;

/** Models an assembler instruction */
public abstract class Instruction implements GNUAssemblerConvertible {
  public static enum Type {
    MOV("movq");
    /** GNU Assembler name */
    public final String asm;

    Type(String asmInstruction) {
      this.asm = asmInstruction;
    }
  }

  /** Comments that belong to this instruction */
  private List<String> comments = new ArrayList<>();

  public void addComment(String comment) {
    comments.add(comment);
  }

  @Override
  public String toGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    if (comments.size() > 0) {
      builder.append("/*");
      for (String comment : comments) {
        builder.append("\t\n").append(comment);
      }
      builder.append("\n*/\n");
    }
    return builder.toString();
  }

  public abstract Type getType();
}
