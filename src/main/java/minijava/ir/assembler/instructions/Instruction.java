package minijava.ir.assembler.instructions;

import static org.jooq.lambda.Seq.seq;

import com.google.common.base.Splitter;
import firm.nodes.Node;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import minijava.ir.assembler.GNUAssemblerConvertible;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.location.*;
import org.jetbrains.annotations.NotNull;

/** Models an assembler instruction */
public abstract class Instruction implements GNUAssemblerConvertible, Comparable<Instruction> {

  public static enum Type {
    ADD("add", true),
    SUB("sub", true),
    MUL("imul", true),
    DIV("idivl", false),
    NEG("neg", true),
    CLTD("cltd"),
    CMP(Category.CMP, "cmp", true),
    COND_JMP(Category.JMP, "j"),
    JMP(Category.JMP, "jmp"),
    SET(Category.AFTER_CMP, "set"),
    PUSH("pushq"),
    POP("pop", true),
    RET("ret"),
    CALL("call"),
    ALLOC_STACK("subq"),
    DEALLOC_STACK("addq"),
    AND("and", false),
    MOV("mov", true),
    EVICT(Category.META, "evict"),
    PROLOGUE(Category.META, "prologue"),
    META_CALL(Category.META, "meta_call"),
    META_LOAD(Category.META, "meta_load"),
    META_STORE(Category.META, "meta_store"),
    META_FRAME_ALLOC(Category.META, "frame_alloc");

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
    NORMAL,
    META
  }

  /** Width of the arguments (and therefore the result most of the time) */
  public final Register.Width width;

  protected Instruction(Register.Width width) {
    this.width = width;
  }

  public static Register.Width getWidthOfArguments(Class klass, Argument... arguments) {
    Register.Width width = arguments[0].width;
    for (int i = 1; i < arguments.length; i++) {
      Argument argument = arguments[i];
      if (argument.width != width) {
        throw new RuntimeException(
            String.format(
                "%s %s: Argument %d has invalid width %s, expected %s",
                klass.getSimpleName(),
                seq(Arrays.asList(arguments))
                    .map(Argument::toString)
                    .stream()
                    .collect(Collectors.joining(" ")),
                i,
                argument.width,
                width));
      }
    }
    return width;
  }

  /** Comments that belong to this instruction */
  private List<String> comments = new ArrayList<>();

  private Node associatedFirmNode = null;

  private Optional<CodeBlock> parentBlock = Optional.empty();
  private Optional<Integer> numberInSegment = Optional.empty();

  public final void addComment(String comment) {
    comments.add(comment);
  }

  /**
   * Produces GNU assembler code with comments. Places the comments behind the assembler
   * instructions. It doesn't end with a line break, but might contain line breaks.
   */
  @Override
  public final String toGNUAssembler() {
    final int COMMAND_WITH = 50;
    final int LINE_WIDTH = 100 - 6;
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
    maxWidthOfFirstLine = Math.max(Math.min(maxWidthOfFirstLine, maxWidth), 1);
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

  public Node firm() {
    return associatedFirmNode;
  }

  /** Takes into account the instructions with varying width arguments */
  protected String getAsmInstructionName() {
    if (getType().hasVaryingWidthArguments) {
      return getType().asm + width.asm;
    }
    return getType().asm;
  }

  @Override
  public String toString() {
    return toGNUAssembler();
  }

  public void setParentBlock(CodeBlock codeBlock) {
    if (this.parentBlock.isPresent()) {
      throw new RuntimeException();
    }
    this.parentBlock = Optional.of(codeBlock);
  }

  public CodeBlock getParentBlock() {
    return parentBlock.get();
  }

  public void setNumberInSegment(int number) {
    this.numberInSegment = Optional.of(number);
  }

  public int getNumberInSegment() {
    return numberInSegment.get();
  }

  public boolean isMetaInstruction() {
    return getType().category == Category.META;
  }

  public abstract List<Argument> getArguments();

  public void setUsedByRelations() {
    for (Argument argument : getArguments()) {
      argument.addUsedByRelation(this);
    }
  }

  /** Can only be used if the "number in block" is already set. */
  @Override
  public int compareTo(@NotNull Instruction other) {
    return Integer.compare(this.getNumberInSegment(), other.getNumberInSegment());
  }

  public abstract <T> T accept(InstructionVisitor<T> visitor);

  public Instruction firmAndComments(Instruction other) {
    this.comments = other.comments;
    this.associatedFirmNode = other.associatedFirmNode;
    return this;
  }
}
