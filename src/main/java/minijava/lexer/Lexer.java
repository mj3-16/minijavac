package minijava.lexer;

import static minijava.token.Terminal.*;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Iterator;
import minijava.MJError;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.SourcePosition;
import minijava.util.SourceRange;

/** SLL(1) parser style lexer implementation. */
public class Lexer implements Iterator<Token> {

  static final ImmutableMap<String, Terminal> KEYWORDS =
      Maps.uniqueIndex(
          EnumSet.of(
              BOOLEAN, CLASS, ELSE, FALSE, IF, INT, NEW, NULL, PUBLIC, RETURN, STATIC, THIS, TRUE,
              VOID, WHILE),
          t -> t.string);

  static final ImmutableSet<String> RESERVED_IDENTIFIERS =
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

  private final InputStream input;
  private int ch = -2;
  private int line = 1;
  private int column = -1; // after calling nextChar the first time, this will be 0
  private Token eof;
  private SourcePosition tokenBegin;
  private boolean inlineNUL = false;
  private int currentTokenNumber = 0;

  public Lexer(InputStream input) {
    this.input = new BufferedInputStream(input);
    nextChar();
  }

  public Lexer(byte[] input) {
    this.input = new ByteArrayInputStream(input);
    nextChar();
  }

  /**
   * Converts {@code input} using {@link String#getBytes(Charset)} with charset {@link
   * StandardCharsets#US_ASCII}
   */
  public Lexer(String input) {
    this(input.getBytes(StandardCharsets.US_ASCII));
  }

  private void nextChar() {
    try {
      ch = input.read();
      if (ch > 127 || ch < -1) {
        throw new LexerError(
            new SourcePosition(currentTokenNumber, line, column),
            String.format("Unsupported character with code %d", ch));
      }
      if (ch == '\n') {
        column = -1;
        line++;
      } else {
        column++;
      }
      inlineNUL = ch == 0 && input.available() > 0;
    } catch (IOException e) {
      throw new MJError(e);
    }
  }

  private Token scan() {
    while (true) {
      skipWhitespace();
      tokenBegin = new SourcePosition(currentTokenNumber, line, column);
      if (isDigit(ch)) {
        return scanInt();
      }
      if (isAlpha(ch) || ch == '_') {
        return scanKeywordOrIdentifier();
      }
      switch (ch) {
        case -1:
        case 0:
          if (inlineNUL) {
            throw new LexerError(
                new SourcePosition(currentTokenNumber, line, column), "Invalid NUL byte");
          }
          return (eof = createToken(EOF));
        case '+':
          nextChar();
          return scanPlus();
        case '>':
          nextChar();
          return scanGreater();
        case '(':
          nextChar();
          return createToken(LPAREN);
        case ')':
          nextChar();
          return createToken(RPAREN);
        case '?':
          nextChar();
          return createToken(RESERVED, "?");
        case ';':
          nextChar();
          return createToken(SEMICOLON);
        case '[':
          nextChar();
          return createToken(LBRACK);
        case ']':
          nextChar();
          return createToken(RBRACK);
        case '/':
          nextChar();
          Token t = scanSlash();
          if (t == null) {
            continue; // skip comments
          }
          return t;
        case '-':
          nextChar();
          return scanMinus();
        case '{':
          nextChar();
          return createToken(LBRACE);
        case '}':
          nextChar();
          return createToken(RBRACE);
        case ':':
          nextChar();
          return createToken(RESERVED, ":");
        case ',':
          nextChar();
          return createToken(COMMA);
        case '%':
          nextChar();
          return scanModulo();
        case '.':
          nextChar();
          return createToken(PERIOD);
        case '<':
          nextChar();
          return scanLower();
        case '=':
          nextChar();
          return scanEqual();
        case '!':
          nextChar();
          return scanInvert();
        case '&':
          nextChar();
          return scanAnd();
        case '~':
          nextChar();
          return createToken(RESERVED, "~");
        case '*':
          nextChar();
          return scanStar();
        case '|':
          nextChar();
          return scanPipe();
        case '^':
          nextChar();
          return scanCaret();
      }
      throw new LexerError(
          tokenBegin, String.format("tokens must not start with character '%c' (%d)", ch, ch));
    }
  }

  private void skipWhitespace() {
    while (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
      nextChar();
    }
  }

  private boolean isDigit(int ch) {
    return ch >= '0' && ch <= '9';
  }

  private boolean isAlpha(int ch) {
    return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z');
  }

  private Token scanInvert() {
    switch (ch) {
      case '=':
        nextChar();
        return createToken(NEQ);
      default:
        return createToken(NOT);
    }
  }

  private Token scanCaret() {
    switch (ch) {
      case '=':
        nextChar();
        return createToken(RESERVED, "^=");
      default:
        return createToken(RESERVED, "^");
    }
  }

  private Token scanModulo() {
    switch (ch) {
      case '=':
        nextChar();
        return createToken(RESERVED, "%=");
      default:
        return createToken(MOD);
    }
  }

  private Token scanInt() {
    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(ch);
    if (ch == '0') {
      nextChar();
    } else {
      // first digit in range [1-9]
      nextChar();
      while (isDigit(ch)) {
        builder.appendCodePoint(ch);
        nextChar();
      }
    }
    return createToken(INTEGER_LITERAL, builder.toString());
  }

  private Token scanPlus() {
    switch (ch) {
      case '+':
        nextChar();
        return createToken(RESERVED, "++");
      case '=':
        nextChar();
        return createToken(RESERVED, "+=");
      default:
        return createToken(ADD);
    }
  }

  private Token scanGreater() {
    switch (ch) {
      case '>':
        nextChar();
        switch (ch) {
          case '>':
            nextChar();
            switch (ch) {
              case '=':
                nextChar();
                return createToken(RESERVED, ">>>=");
              default:
                return createToken(RESERVED, ">>>");
            }
          case '=':
            nextChar();
            return createToken(RESERVED, ">>=");
          default:
            return createToken(RESERVED, ">>");
        }
      case '=':
        nextChar();
        return createToken(GEQ);
      default:
        return createToken(GTR);
    }
  }

  private Token scanSlash() {
    switch (ch) {
      case '*':
        nextChar();
        return scanCommentRest();
      case '=':
        nextChar();
        return createToken(RESERVED, "/=");
      default:
        return createToken(DIV);
    }
  }

  private Token scanCommentRest() {
    while (true) {
      int prev = ch;
      nextChar();
      if (prev == -1 || ch == -1) {
        throw new LexerError(
            new SourcePosition(currentTokenNumber, line, column),
            "Reached EOF, but comment starting at " + tokenBegin + " is not complete");
      }
      if (prev == '*' && ch == '/') {
        nextChar();
        return null;
      }
    }
  }

  private Token scanMinus() {
    switch (ch) {
      case '=':
        nextChar();
        return createToken(RESERVED, "-=");
      case '-':
        nextChar();
        return createToken(RESERVED, "--");
      default:
        return createToken(SUB);
    }
  }

  private Token scanLower() {
    switch (ch) {
      case '<':
        nextChar();
        switch (ch) {
          case '=':
            nextChar();
            return createToken(RESERVED, "<<=");
          default:
            return createToken(RESERVED, "<<");
        }
      case '=':
        nextChar();
        return createToken(LEQ);
      default:
        return createToken(LSS);
    }
  }

  private Token scanEqual() {
    switch (ch) {
      case '=':
        nextChar();
        return createToken(EQL);
      default:
        return createToken(ASSIGN);
    }
  }

  private Token scanAnd() {
    switch (ch) {
      case '&':
        nextChar();
        return createToken(AND);
      case '=':
        nextChar();
        return createToken(RESERVED, "&=");
      default:
        return createToken(RESERVED, "&");
    }
  }

  private Token scanStar() {
    switch (ch) {
      case '=':
        nextChar();
        return createToken(RESERVED, "*=");
      default:
        return createToken(MUL);
    }
  }

  private Token scanPipe() {
    switch (ch) {
      case '|':
        nextChar();
        return createToken(OR);
      case '=':
        nextChar();
        return createToken(RESERVED, "|=");
      default:
        return createToken(RESERVED, "|");
    }
  }

  private Token scanKeywordOrIdentifier() {
    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(ch);
    nextChar();
    while (ch != -1 && (isAlpha(ch) || ch == '_' || isDigit(ch))) {
      builder.appendCodePoint(ch);
      nextChar();
    }
    String word = builder.toString();
    Terminal keywordTerminal = KEYWORDS.get(word);
    if (keywordTerminal != null) {
      return createToken(keywordTerminal);
    }
    if (RESERVED_IDENTIFIERS.contains(word)) {
      return createToken(RESERVED, word);
    }
    return createToken(IDENT, builder.toString());
  }

  private Token createToken(Terminal terminal, String content) {
    SourceRange range = new SourceRange(tokenBegin, content.length());
    currentTokenNumber++;
    return new Token(terminal, range, content);
  }

  private Token createToken(Terminal terminal) {
    // terminal.string == null can only happen if terminal == EOF
    int length = terminal.string == null ? 1 : terminal.string.length();
    SourceRange range = new SourceRange(tokenBegin, length);
    currentTokenNumber++;
    return new Token(terminal, range, null);
  }

  @Override
  public boolean hasNext() {
    return eof == null;
  }

  @Override
  public Token next() {
    if (eof != null) {
      return eof;
    }
    return scan();
  }
}
