package minijava.token;

import static minijava.token.Terminal.Associativity.LEFT;
import static minijava.token.Terminal.Associativity.RIGHT;

import java.util.Optional;

/** Enum of terminals used by the Lexer. */
public enum Terminal {

  // keywords
  BOOLEAN("boolean"),
  CLASS("class"),
  ELSE("else"),
  FALSE("false"),
  IF("if"),
  INT("int"),
  NEW("new"),
  NULL("null"),
  PUBLIC("public"),
  RETURN("return"),
  STATIC("static"),
  THIS("this"),
  TRUE("true"),
  VOID("void"),
  WHILE("while"),

  // operators
  INVERT("!", LEFT, 0),
  EQUAL_SIGN("=", RIGHT, 1),
  OR("||", LEFT, 2),
  AND("&&", LEFT, 3),
  EQUALS("==", LEFT, 4),
  UNEQUALS("!=", LEFT, 4),
  LOWER("<", LEFT, 5),
  LOWER_EQUALS("<=", LEFT, 5),
  GREATER(">", LEFT, 5),
  GREATER_EQUALS(">=", LEFT, 5),
  PLUS("+", LEFT, 6),
  MINUS("-", LEFT, 6),
  MULTIPLY("*", LEFT, 7),
  DIVIDE("/", LEFT, 7),
  MODULO("%", LEFT, 7),

  // separators
  LPAREN("("),
  RPAREN(")"),
  LBRACKET("["),
  RBRACKET("]"),
  LCURLY("{"),
  RCURLY("}"),
  DOT("."),
  COMMA(","),
  SEMICOLON(";"),

  // with dynamic string values (lexval in Token is not null for tokens of this types)
  IDENT,
  INTEGER_LITERAL,
  RESERVED, // reserved keyword or operator, parsing fails if tokens of this type exist

  // others - possible candidates for removal (except for EOF)!
  EOF,

  COMMENT,
  WS,
  QUESTION_MARK("?"),
  COLON(":");

  public enum Associativity {
    LEFT,
    RIGHT
  }

  public final Optional<String> string;
  final Optional<Associativity> associativity;
  final Optional<Integer> precedence;

  Terminal(String string, Associativity associativity, Integer precedence) {
    this.string = Optional.ofNullable(string);
    this.associativity = Optional.ofNullable(associativity);
    this.precedence = Optional.ofNullable(precedence);
    assert this.associativity.isPresent() == this.precedence.isPresent();
  }

  Terminal(String string) {
    this(string, null, null);
  }

  Terminal() {
    this(null, null, null);
  }
}
