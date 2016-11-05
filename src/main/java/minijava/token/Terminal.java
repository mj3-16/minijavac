package minijava.token;

import static minijava.token.Terminal.Associativity.LEFT;
import static minijava.token.Terminal.Associativity.RIGHT;

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
  NOT("!", LEFT, 0),
  ASSIGN("=", RIGHT, 1),
  OR("||", LEFT, 2),
  AND("&&", LEFT, 3),
  EQL("==", LEFT, 4),
  NEQ("!=", LEFT, 4),
  LSS("<", LEFT, 5),
  LEQ("<=", LEFT, 5),
  GTR(">", LEFT, 5),
  GEQ(">=", LEFT, 5),
  ADD("+", LEFT, 6),
  SUB("-", LEFT, 6),
  MUL("*", LEFT, 7),
  DIV("/", LEFT, 7),
  MOD("%", LEFT, 7),

  // separators
  LPAREN("("),
  RPAREN(")"),
  LBRACK("["),
  RBRACK("]"),
  LBRACE("{"),
  RBRACE("}"),
  COMMA(","),
  PERIOD("."),
  SEMICOLON(";"),

  // with dynamic string values (lexval in Token is not null for tokens of this types)
  IDENT,
  INTEGER_LITERAL,
  RESERVED, // reserved keyword or operator, parsing fails if tokens of this type exist

  // others
  EOF;

  public enum Associativity {
    LEFT,
    RIGHT
  }

  public final String string;
  final Associativity associativity;
  final Integer precedence;

  Terminal(String string, Associativity associativity, Integer precedence) {
    assert (associativity == null) == (precedence == null);
    this.string = string;
    this.associativity = associativity;
    this.precedence = precedence;
  }

  Terminal(String string) {
    this(string, null, null);
  }

  Terminal() {
    this(null, null, null);
  }
}
