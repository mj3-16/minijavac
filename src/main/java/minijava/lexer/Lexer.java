package minijava.lexer;

import static minijava.token.Terminal.*;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.ByteArrayInputStream;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import minijava.MJError;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;

/** SLL(1) parser style lexer implementation. */
public class Lexer implements Iterator<Token> {

  private final LexerInput input;
  private Token token;
  private Position position;

  static final ImmutableMap<String, Terminal> keywords =
      Maps.uniqueIndex(
          EnumSet.of(
              BOOLEAN, CLASS, ELSE, FALSE, IF, INT, NEW, NULL, PUBLIC, RETURN, STATIC, THIS, TRUE,
              VOID, WHILE),
          t -> t.string);

  static final ImmutableSet<String> reservedIdentifiers =
      ImmutableSet.of(
          "abstract",
          "assert",
          "break",
          "byte",
          "case",
          "catch",
          "char",
          "const",
          "continue",
          "default",
          "double",
          "do",
          "enum",
          "extends",
          "finally",
          "final",
          "float",
          "for",
          "goto",
          "implements",
          "import",
          "instanceof",
          "interface",
          "long",
          "native",
          "package",
          "private",
          "protected",
          "short",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "throws",
          "throw",
          "transient",
          "try",
          "volatile");

  public Lexer(LexerInput input) {
    this.input = input;
  }

  private Token parseNextToken() {
    if (input.current() > 127) {
      throw new LexerError(
          input.getCurrentPosition(), String.format("Invalid char number %d", input.current()));
    }
    if (input.current() <= 0) {
      position = input.getCurrentPosition();
      return createToken(EOF);
    }
    omitWS();
    if (input.current() <= 0) {
      position = input.getCurrentPosition();
      return createToken(EOF);
    }
    position = input.getCurrentPosition();
    byte cur = input.current();
    if (Character.isDigit(cur)) {
      return parseInt();
    }
    if (isAlphabet(cur) || cur == '_') {
      return parseKeywordOrIdentifier();
    }
    switch (cur) {
      case '+':
        input.next();
        return parsePlus();
      case '>':
        input.next();
        return parseGreater();
      case '(':
        input.next();
        return createToken(LPAREN);
      case ')':
        input.next();
        return createToken(RPAREN);
      case '?':
        input.next();
        return createToken(QUESTION_MARK);
      case ';':
        input.next();
        return createToken(SEMICOLON);
      case '[':
        input.next();
        return createToken(LBRACK);
      case ']':
        input.next();
        return createToken(RBRACK);
      case '/':
        input.next();
        return parseSlash();
      case '-':
        input.next();
        return parseMinus();
      case '{':
        input.next();
        return createToken(LBRACE);
      case '}':
        input.next();
        return createToken(RBRACE);
      case ':':
        input.next();
        return createToken(COLON);
      case ',':
        input.next();
        return createToken(COMMA);
      case '%':
        input.next();
        return parseModulo();
      case '.':
        input.next();
        return createToken(PERIOD);
      case '<':
        input.next();
        return parseLower();
      case '=':
        input.next();
        return parseEqual();
      case '!':
        input.next();
        return parseInvert();
      case '&':
        input.next();
        return parseAnd();
      case '~':
        input.next();
        return createToken(RESERVED, "~");
      case '*':
        input.next();
        return parseStar();
      case '|':
        input.next();
        return parsePipe();
      case '^':
        input.next();
        return parseCaret();
      default:
        throw createError();
    }
  }

  private MJError createError() {
    return new LexerError(
        input.getCurrentPosition(),
        String.format("Unexpected character '%s'(%d)", input.current(), input.current()));
  }

  private void omitWS() {
    while (input.isCurrentChar(' ', '\n', '\r', '\t')) {
      input.next();
    }
  }

  private Token parseInvert() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(NEQ);
      default:
        return createToken(NOT);
    }
  }

  private Token parseCaret() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED, "^=");
      default:
        return createToken(RESERVED, "^");
    }
  }

  private Token parseModulo() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED, "%=");
      default:
        return createToken(MOD);
    }
  }

  private Token parseInt() {
    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(input.current());
    if (input.current() == '0') {
      input.next();
    } else {
      // range [1-9]
      byte cur = input.next();
      while (cur >= '0' && cur <= '9') {
        builder.appendCodePoint(cur);
        cur = input.next();
      }
    }
    return createToken(INTEGER_LITERAL, builder.toString());
  }

  private Token parsePlus() {
    switch (input.current()) {
      case '+':
        input.next();
        return createToken(RESERVED, "++");
      case '=':
        input.next();
        return createToken(RESERVED, "+=");
      default:
        return createToken(ADD);
    }
  }

  private Token parseGreater() {
    switch (input.current()) {
      case '>':
        input.next();
        switch (input.current()) {
          case '>':
            input.next();
            switch (input.current()) {
              case '=':
                input.next();
                return createToken(RESERVED, ">>>=");
              default:
                return createToken(RESERVED, ">>>");
            }
          case '=':
            input.next();
            return createToken(RESERVED, ">>=");
          default:
            return createToken(RESERVED, ">>");
        }
      case '=':
        input.next();
        return createToken(GEQ);
      default:
        return createToken(GTR);
    }
  }

  private Token parseSlash() {
    switch (input.current()) {
      case '*':
        input.next();
        return parseCommentRest();
      case '=':
        input.next();
        return createToken(RESERVED, "/=");
      default:
        return createToken(DIV);
    }
  }

  private Token parseCommentRest() {
    while (true) {
      byte cur = input.current();
      byte next = input.next();
      if (cur < 0 || next < 0) {
        throw createError();
      }
      if (cur == '*' && next == '/') {
        input.next();
        return createToken(COMMENT);
      }
    }
  }

  private Token parseMinus() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED, "-=");
      case '-':
        input.next();
        return createToken(RESERVED, "--");
      default:
        return createToken(SUB);
    }
  }

  private Token parseLower() {
    switch (input.current()) {
      case '<':
        input.next();
        switch (input.current()) {
          case '=':
            input.next();
            return createToken(RESERVED, "<<=");
          default:
            return createToken(RESERVED, "<<");
        }
      case '=':
        input.next();
        return createToken(LEQ);
      default:
        return createToken(LSS);
    }
  }

  private Token parseEqual() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(EQL);
      default:
        return createToken(ASSIGN);
    }
  }

  private Token parseAnd() {
    switch (input.current()) {
      case '&':
        input.next();
        return createToken(AND);
      case '=':
        input.next();
        return createToken(RESERVED, "&=");
      default:
        return createToken(RESERVED, "&");
    }
  }

  private Token parseStar() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED, "*=");
      default:
        return createToken(MUL);
    }
  }

  private Token parsePipe() {
    switch (input.current()) {
      case '|':
        input.next();
        return createToken(OR);
      case '=':
        input.next();
        return createToken(RESERVED, "|=");
      default:
        return createToken(RESERVED, "|");
    }
  }

  private Token parseKeywordOrIdentifier() {
    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(input.current());
    byte cur = (byte) (int) input.next();
    while (input.current() != -1 && (isAlphabet(cur) || cur == '_' || Character.isDigit(cur))) {
      builder.appendCodePoint(cur);
      cur = (byte) (int) input.next();
    }
    String word = builder.toString();
    Terminal keywordTerminal = keywords.get(word);
    if (keywordTerminal != null) {
      return createToken(keywordTerminal, word);
    }
    if (reservedIdentifiers.contains(word)) {
      return createToken(RESERVED, word);
    }
    return createToken(IDENT, builder.toString());
  }

  private boolean isAlphabet(byte c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  private Token createToken(Terminal terminal, String content) {
    return new Token(terminal, position, content);
  }

  private Token createToken(Terminal terminal) {
    return new Token(terminal, position, null);
  }

  @Override
  public boolean hasNext() {
    return token == null || token.terminal != EOF;
  }

  @Override
  public Token next() {
    do {
      if (token != null && token.terminal == EOF) {
        // In case we are at the EOF, we just keep returning that
        return token;
      }
      // otherwise keep reading in the next token
      token = parseNextToken();
      // ... as long as we are hitting comments.
    } while (token.terminal == COMMENT);

    return token;
  }

  public static List<Token> getAllTokens(String input) {
    return seq(new Lexer(new BasicLexerInput(new ByteArrayInputStream(input.getBytes()))))
        .collect(Collectors.toList());
  }
}
