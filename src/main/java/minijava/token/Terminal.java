package minijava.token;

import static minijava.token.Terminal.TerminalType.CONTROL_FLOW;
import static minijava.token.Terminal.TerminalType.HIDDEN;
import static minijava.token.Terminal.TerminalType.LITERAL;
import static minijava.token.Terminal.TerminalType.MISC;
import static minijava.token.Terminal.TerminalType.OPERATOR;
import static minijava.token.Terminal.TerminalType.SYNTAX_ELEMENT;
import static minijava.token.Terminal.TerminalType.TYPE;

/** Enum of terminals used by the Lexer. */
public enum Terminal {
  EOF("eof", MISC),
  COMMENT("comment", HIDDEN),
  WS("white space", HIDDEN),
  LOWER_EQUALS("<=", OPERATOR, 0, true),
  GREATER_EQUALS(">=", OPERATOR, 0, true),
  MODULO("%", OPERATOR, 0, true),
  LBRACKET("[", SYNTAX_ELEMENT),
  RBRACKET("]", SYNTAX_ELEMENT),
  PLUS("+", OPERATOR, 0, true),
  MINUS("-", OPERATOR, 0, true),
  DIVIDE("/", OPERATOR, 0, true),
  MULTIPLY("*", OPERATOR, 0, true),
  EQUAL_SIGN("=", OPERATOR, 0, false),
  EQUALS("==", OPERATOR, 0, true),
  UNEQUALS("!=", OPERATOR, 0, true),
  INVERT("!", OPERATOR, 0, true),
  LOWER("<", OPERATOR, 0, true),
  GREATER(">", OPERATOR, 0, true),
  AND("&&", OPERATOR, 0, true),
  OR("||", OPERATOR, 0, true),
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
  private final TerminalType terminalType;
  private final int precedence;
  private final boolean leftAssociative;

  private Terminal(String description, TerminalType terminalType, int precedence, boolean leftAssociative) {
    this.description = description;
    this.terminalType = terminalType;
    this.precedence = precedence;
    this.leftAssociative = leftAssociative;
  }

  private Terminal(String description, TerminalType terminalType) {
    this(description, terminalType, -1, true);
  }

  public String getDescription() {
    return description;
  }

  private static Terminal[] terminals = values();

  public static Terminal valueOf(int id) {
    return terminals[id];
  }

  public TerminalType getType() {
    return terminalType;
  }

  public boolean isType(TerminalType terminalType) {
    return this.terminalType == terminalType;
  }

  public int getPrecedence() { return this.precedence; }

  public boolean isLeftAssociative() { return this.leftAssociative; }
}
