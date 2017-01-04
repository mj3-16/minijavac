package minijava.ir.assembler.instructions;

import com.google.common.base.Splitter;
import firm.nodes.Node;
import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.location.Register;

/** Models an assembler instruction */
public abstract class Instruction implements GNUAssemblerConvertible {

  public static enum Type {
    ADD("add", true),
    SUB("sub", true),
    MUL("imul", true),
    DIV("idivl", false),
    NEG("neg", true),
    CLTD("cltd"),
    CMP(Category.CMP, "cmp", true),
    JMP_LESS(Category.JMP, "jl"),
    JMP_LESS_OR_EQUAL(Category.JMP, "jle"),
    JMP_GREATER(Category.JMP, "jg"),
    JMP_GREATER_OR_EQUAL(Category.JMP, "jge"),
    JMP_EQUAL(Category.JMP, "je"),
    JMP(Category.JMP, "jmp"),
    SET(Category.AFTER_CMP, "set"),
    PUSH("pushq"),
    POP("pop", true),
    RET("ret"),
    CALL("call"),
    ALLOC_STACK("subq"),
    DEALLOC_STACK("addq"),
    AND("and", false),
    MOV("mov", true);

    public final Category category;
    /** GNU Assembler name (without argument width appendix) */
    public final String asm;

    public final boolean hasVaryingWidthArguments;

    Type(Category category, String asm, boolean hasVaryingWidthArguments) {
      this.category = category;
      this.asm = asm;
      this.hasVaryingWidthArguments = hasVaryingWidthArguments;
    }

    Type(Category category, String asm) {
      this(category, asm, false);
    }

    Type(String asm) {
      this(Category.NORMAL, asm, false);
    }

    Type(String asm, boolean hasVaryingWidthArguments) {
      this(Category.NORMAL, asm, hasVaryingWidthArguments);
    }
  }

  /** Enum that helps to separate jmp and cmp like instructions from the rest */
  public static enum Category {
    JMP,
    CMP,
    AFTER_CMP,
    NORMAL
  }

  /** Comments that belong to this instruction */
  private List<String> comments = new ArrayList<>();

  private Node associatedFirmNode = null;

  public final void addComment(String comment) {
    comments.add(comment);
  }

  /**
   * Produces GNU assembler code with comments. Places the comments behind the assembler
   * instructions. It doesn't end with a line break, but might contain line breaks.
   */
  @Override
  public final String toGNUAssembler() {
    final int COMMAND_WITH = 30;
    final int LINE_WIDTH = 80 - 6;
    String fmt = "    %-" + COMMAND_WITH + "s%s";
    String asm = toGNUAssemblerWoComments();
    if (comments.size() > 0) {
      StringBuilder builder = new StringBuilder();
      List<String> commentLines =
          formatComments(LINE_WIDTH - asm.length() - 1, LINE_WIDTH - COMMAND_WITH - 1);
      builder.append(String.format(fmt, asm, commentLines.get(0)));
      for (int i = 1; i < commentLines.size(); i++) {
        builder.append(System.lineSeparator());
        builder.append(String.format(fmt, "", commentLines.get(i)));
      }
      return builder.toString();
    } else {
      return String.format(fmt, asm, "");
    }
  }

  private List<String> formatComments(int maxWidthOfFirstLine, int maxWidth) {
    List<String> lines = new ArrayList<>();
    String joined = String.join("; ", comments) + " */";
    maxWidthOfFirstLine = Math.min(maxWidthOfFirstLine, maxWidth);
    if (joined.length() > maxWidthOfFirstLine) {
      lines.add("/* " + joined.substring(0, maxWidthOfFirstLine - 1));
      for (String line :
          Splitter.fixedLength(maxWidth).split(joined.substring(maxWidthOfFirstLine - 1))) {
        lines.add("   " + line);
      }
      lines.set(lines.size() - 1, lines.get(lines.size() - 1));
    } else {
      lines.add("/* " + joined);
    }
    return lines;
  }

  /**
   * Produces the GNU assembler for this instruction without any comments. It should produce a
   * single line of output without a line break at the end
   */
  protected String toGNUAssemblerWoComments() {
    return getAsmInstructionName();
  }

  /** Produces the GNU assembler for the given arguments without comments */
  protected final String createGNUAssemblerWoComments(Argument... arguments) {
    StringBuilder builder = new StringBuilder();
    builder.append(getAsmInstructionName());
    if (arguments.length > 0) {
      builder.append(" ");
    }
    for (int i = 0; i < arguments.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      builder.append(arguments[i].toGNUAssembler());
    }
    return builder.toString();
  }

  public abstract Type getType();

  public boolean isJmpOrCmpLike() {
    return getType().category == Category.JMP || getType().category == Category.CMP;
  }

  /**
   * Add a comment
   *
   * @return the current instance
   */
  public Instruction com(String comment) {
    comments.add(comment);
    return this;
  }

  public Instruction firm(Node node) {
    associatedFirmNode = node;
    comments.add(0, node.toString());
    return this;
  }

  /** Takes into account the instructions with varying width arguments */
  protected String getAsmInstructionName() {
    if (getType().hasVaryingWidthArguments) {
      return getType().asm + getWidthOfArguments().asm;
    }
    return getType().asm;
  }

  /** Returns the width of the arguments for instructions with varying argument widths. */
  protected Register.Width getWidthOfArguments() {
    throw new UnsupportedOperationException();
  }

  protected Register.Width getMaxWithOfArguments(Argument... arguments) {
    Register.Width maxWidth = null;
    for (Argument argument : arguments) {
      if (argument instanceof Register) {
        Register.Width argWidth = ((Register) argument).width;
        if (maxWidth == null) {
          maxWidth = argWidth;
        } else if (argWidth.ordinal() > maxWidth.ordinal()) {
          maxWidth = argWidth;
        }
      }
    }
    if (maxWidth == null) {
      maxWidth = Register.Width.Long;
    }
    return maxWidth;
  }

  @Override
  public String toString() {
    return toGNUAssembler();
  }
}
