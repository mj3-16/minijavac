package minijava.ir.assembler.instructions;

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
    PUSH("push", true),
    POP("pop", true),
    RET("ret"),
    CALL("call"),
    ALLOC_STACK("subq"),
    DEALLOC_STACK("addq"),
    AND("and", true),
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
    NORMAL
  }

  /** Comments that belong to this instruction */
  private List<String> comments = new ArrayList<>();

  private Node associatedFirmNode = null;

  public void addComment(String comment) {
    comments.add(comment);
  }

  /**
   * Produces GNU assembler code that only consists of the comments and the assembler instructions
   * name.
   */
  @Override
  public String toGNUAssembler() {
    return commentsToGNUAssembler() + "\t" + getAsmInstructionName();
  }

  /** Returns the comments as a GNU assembler string */
  protected String commentsToGNUAssembler() {
    StringBuilder builder = new StringBuilder();
    if (comments.size() > 0) {
      builder.append("/*");
      builder.append(String.join("\n\t", comments));
      builder.append("*/");
      builder.append("\n");
    }
    return builder.toString();
  }

  /**
   * Produces the GNU assembler for the given arguments (with the comments and the assembler
   * instruction)
   */
  protected String toGNUAssembler(Argument... arguments) {
    StringBuilder builder = new StringBuilder();
    builder.append("\n");
    builder.append(commentsToGNUAssembler());
    builder.append("\t");
    builder.append(getAsmInstructionName());
    builder.append(" ");
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
}
