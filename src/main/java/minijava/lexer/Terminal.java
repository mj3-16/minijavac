package minijava.lexer;

import static minijava.lexer.Terminal.TerminalType.CONTROL_FLOW;
import static minijava.lexer.Terminal.TerminalType.HIDDEN;
import static minijava.lexer.Terminal.TerminalType.LITERAL;
import static minijava.lexer.Terminal.TerminalType.MISC;
import static minijava.lexer.Terminal.TerminalType.OPERATOR;
import static minijava.lexer.Terminal.TerminalType.SYNTAX_ELEMENT;
import static minijava.lexer.Terminal.TerminalType.TYPE;

/** Enum of terminals used by the Lexer. */
public enum Terminal {
  EOF("eof", MISC),
  COMMENT("comment", HIDDEN),
  WS("white space", HIDDEN),
  LOWER_EQUALS("<=", OPERATOR),
  GREATER_EQUALS(">=", OPERATOR),
  MODULO("%", OPERATOR),
  LBRACKET("[", SYNTAX_ELEMENT),
  RBRACKET("]", SYNTAX_ELEMENT),
  PLUS("+", OPERATOR),
  MINUS("-", OPERATOR),
  DIVIDE("/", OPERATOR),
  MULTIPLY("*", OPERATOR),
  EQUAL_SIGN("=", OPERATOR),
  EQUALS("==", OPERATOR),
  UNEQUALS("!=", OPERATOR),
  INVERT("!", OPERATOR),
  LOWER("<", OPERATOR),
  GREATER(">", OPERATOR),
  AND("&&", OPERATOR),
  OR("||", OPERATOR),
  LPAREN("(", SYNTAX_ELEMENT),
  RPAREN(")", SYNTAX_ELEMENT),
  QUESTION_MARK("?", SYNTAX_ELEMENT),
  SEMICOLON(";", SYNTAX_ELEMENT),
  INTEGER_LITERAL("int", LITERAL),
  IDENT("identifier", LITERAL),
  LCURLY("{", SYNTAX_ELEMENT),
  RCURLY("}", SYNTAX_ELEMENT),
  COLON(":", SYNTAX_ELEMENT),
  COMMA(",", SYNTAX_ELEMENT),
  DOT(".", SYNTAX_ELEMENT),
  RESERVED_OPERATORS("reserved operator", OPERATOR),
  RESERVED_IDENTIFIER("reserved identifier", LITERAL),
  BOOLEAN("boolean", TYPE),
  INT("int", TYPE),
  NEW("new", SYNTAX_ELEMENT),
  RETURN("return", SYNTAX_ELEMENT),
  THIS("this", LITERAL),
  IF("if", CONTROL_FLOW),
  WHILE("while", CONTROL_FLOW),
  ELSE("else", CONTROL_FLOW),
  TRUE("true", LITERAL),
  FALSE("false", LITERAL),
  PUBLIC("public", SYNTAX_ELEMENT),
  STATIC("static", SYNTAX_ELEMENT),
  VOID("void", TYPE),
  NULL("null", LITERAL),
  STRING("String", TYPE),
  CLASS("class", SYNTAX_ELEMENT);

  public static enum TerminalType {
    OPERATOR,
    TYPE,
    LITERAL,
    SYNTAX_ELEMENT,
    CONTROL_FLOW,
    HIDDEN,
    MISC
  }

  private final String description;
  private final TerminalType type;

  private Terminal(String description, TerminalType type) {
    this.description = description;
    this.type = type;
  }

  public String getDescription() {
    return description;
  }

  private static Terminal[] terminals = values();

  public static Terminal valueOf(int id) {
    return terminals[id];
  }

  public TerminalType getType() {
    return type;
  }

  public boolean isType(TerminalType type) {
    return this.type == type;
  }
}
