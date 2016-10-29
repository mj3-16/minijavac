package minijava.lexer;

import static minijava.lexer.TaggedWithLocation.tagBytes;
import static minijava.lexer.Terminal.RESERVED_OPERATORS;

import java.io.ByteArrayInputStream;
import java.util.*;
import minijava.MJError;
import minijava.util.InputStreamIterator;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** SLL(1) parser style lexer implementation. */
public class SimpleLexer implements Lexer {

  private static final List<Character> WHITESPACE = Arrays.asList(' ', '\n', '\r', '\t');
  private final Iterator<TaggedWithLocation<Byte>> input;
  private final StringTable stringTable = new StringTable();
  private final Map<Integer, Terminal> keywordTerms = new HashMap<>();
  /** Null if and only if either - We haven't yet called input.next() - input.hasNext() == false */
  private TaggedWithLocation<Byte> current;
  /** Null if and only if we haven't lexed the next token yet. See hasNext. */
  private Token leftOver;

  public SimpleLexer(Iterator<Byte> input) {
    this.input = tagBytes(input);
    keywordTerms.put(StringTable.BOOLEAN_KEYWORD_ID, Terminal.BOOLEAN);
    keywordTerms.put(StringTable.INT_KEYWORD_ID, Terminal.INT);
    keywordTerms.put(StringTable.CLASS_KEYWORD_ID, Terminal.CLASS);
    keywordTerms.put(StringTable.NEW_KEYWORD_ID, Terminal.NEW);
    keywordTerms.put(StringTable.RETURN_KEYWORD_ID, Terminal.RETURN);
    keywordTerms.put(StringTable.THIS_KEYWORD_ID, Terminal.THIS);
    keywordTerms.put(StringTable.WHILE_KEYWORD_ID, Terminal.WHILE);
    keywordTerms.put(StringTable.IF_KEYWORD_ID, Terminal.IF);
    keywordTerms.put(StringTable.ELSE_KEYWORD_ID, Terminal.ELSE);
    keywordTerms.put(StringTable.TRUE_KEYWORD_ID, Terminal.TRUE);
    keywordTerms.put(StringTable.FALSE_KEYWORD_ID, Terminal.FALSE);
    keywordTerms.put(StringTable.PUBLIC_KEYWORD_ID, Terminal.PUBLIC);
    keywordTerms.put(StringTable.STATIC_KEYWORD_ID, Terminal.STATIC);
    keywordTerms.put(StringTable.VOID_KEYWORD_ID, Terminal.VOID);
    keywordTerms.put(StringTable.NULL_KEYWORD_ID, Terminal.NULL);
    keywordTerms.put(StringTable.STRING_KEYWORD_ID, Terminal.STRING);
  }

  @Override
  public StringTable getStringTable() {
    return stringTable;
  }

  @Override
  public boolean hasNext() {
    // Since we emit no tokens for whitespace and comments, we have to actually lex the next token
    if (leftOver == null) {
      if (current == null) {
        if (!input.hasNext()) {
          return false;
        }
        // current is null if and only if we haven't yet started iteration or we previously reached end of input.
        // We made sure there is still input, so we haven't yet started iteration. Normally, only parseNextToken
        // should bump the iterator.
        current = input.next();
      }
      leftOver = parseNextToken();
    }
    return leftOver != null;
  }

  @Override
  @NotNull
  public Token next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    // hasNext will set leftOver, which we can just return
    Token t = leftOver;
    leftOver = null;
    return t;
  }

  private boolean tryAdvance() {
    if (input.hasNext()) {
      current = input.next();
      return true;
    } else {
      current = null;
      return false;
    }
  }

  @Nullable
  private Token parseNextToken() {
    assert current != null;

    // This will modify `current` as we go through `tryAdvance()`

    while (WHITESPACE.contains((char) (byte) current.tagged)) {
      if (!tryAdvance()) return null;
    }

    assert current != null;

    byte cur = current.tagged;
    Location location = current.loc;

    if (cur <= 0) {
      throw new LexerError(location, String.format("Invalid char %c %d", (char) cur, cur & 255));
    }

    if (isDigit(cur)) {
      return parseInt();
    }
    if (isAlphabet(cur) || cur == '_') {
      return parseIdent();
    }

    switch (cur) {
      case '+':
        return parsePlus();
      case '>':
        return parseGreater();
      case '(':
        tryAdvance();
        return createToken(Terminal.LPAREN, location, "(");
      case ')':
        tryAdvance();
        return createToken(Terminal.RPAREN, location, ")");
      case '?':
        tryAdvance();
        return createToken(Terminal.QUESTION_MARK, location, "?");
      case ';':
        tryAdvance();
        return createToken(Terminal.SEMICOLON, location, ";");
      case '[':
        tryAdvance();
        return createToken(Terminal.LBRACKET, location, "[");
      case ']':
        tryAdvance();
        return createToken(Terminal.RBRACKET, location, "]");
      case '/':
        Token token = parseSlash();
        if (token != null) {
          return token;
        }
        return current != null ? parseNextToken() : null;
      case '-':
        return parseMinus();
      case '{':
        tryAdvance();
        return createToken(Terminal.LCURLY, location, "{");
      case '}':
        tryAdvance();
        return createToken(Terminal.RCURLY, location, "}");
      case ':':
        tryAdvance();
        return createToken(Terminal.COLON, location, ":");
      case ',':
        tryAdvance();
        return createToken(Terminal.COMMA, location, ",");
      case '%':
        return parseModulo();
      case '.':
        tryAdvance();
        return createToken(Terminal.DOT, location, ".");
      case '<':
        return parseLower();
      case '=':
        return parseEqual();
      case '!':
        return parseInvert();
      case '&':
        return parseAnd();
      case '~':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "~");
      case '*':
        return parseStar();
      case '|':
        return parsePipe();
      case '^':
        return parseCaret();
      default:
        throw createError(current);
    }
  }

  @Contract("_ -> !null")
  private static MJError createError(TaggedWithLocation<Byte> current) {
    return new LexerError(
        current.loc,
        String.format(
            "Unexpected character '%s'(%d)", (char) (byte) current.tagged, current.tagged));
  }

  private void omitWS() {
    //noinspection StatementWithEmptyBody
  }

  @NotNull
  private Token parseInvert() {
    assert current.tagged == '!';

    Location location = current.loc;

    if (tryAdvance() && current.tagged == '=') {
      tryAdvance();
      return createToken(Terminal.UNEQUALS, location, "!=");
    }

    return createToken(Terminal.INVERT, location, "!");
  }

  @NotNull
  private Token parseCaret() {
    assert current.tagged == '^';

    Location location = current.loc;

    if (tryAdvance() && current.tagged == '=') {
      tryAdvance();
      return createToken(RESERVED_OPERATORS, location, "^=");
    }

    return createToken(RESERVED_OPERATORS, location, "^");
  }

  @NotNull
  private Token parseModulo() {
    assert current.tagged == '%';

    Location location = current.loc;

    if (tryAdvance() && current.tagged == '=') {
      tryAdvance();
      return createToken(Terminal.RESERVED_OPERATORS, location, "%=");
    }

    return createToken(Terminal.MODULO, location, "%");
  }

  @NotNull
  private Token parseInt() {
    assert isDigit(current.tagged);

    Location location = current.loc;

    // absorb leading zeros
    while (current.tagged == '0') {
      if (!tryAdvance() || !isDigit(current.tagged)) {
        // We reached end of input or have only parsed zeros
        return createToken(Terminal.INTEGER_LITERAL, location, "0");
      }
    }

    assert '1' <= current.tagged && current.tagged <= '9';

    // Now lex the actual number

    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(current.tagged);
    while (tryAdvance() && current.tagged >= '0' && current.tagged <= '9') {
      builder.appendCodePoint(current.tagged);
    }
    return createToken(Terminal.INTEGER_LITERAL, location, builder.toString());
  }

  @NotNull
  private Token parsePlus() {
    assert current.tagged == '+';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(Terminal.PLUS, location, "+");
    }

    switch (current.tagged) {
      case '+':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "++");
      case '=':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "+=");
      default:
        return createToken(Terminal.PLUS, location, "+");
    }
  }

  @NotNull
  private Token parseGreater() {
    assert current.tagged == '>';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(Terminal.GREATER, location, ">");
    }

    switch (current.tagged) {
      case '>':
        if (!tryAdvance()) {
          return createToken(RESERVED_OPERATORS, location, ">>");
        }

        switch (current.tagged) {
          case '>':
            if (tryAdvance() && current.tagged == '=') {
              tryAdvance();
              return createToken(RESERVED_OPERATORS, location, ">>>=");
            }

            return createToken(RESERVED_OPERATORS, location, ">>>");
          case '=':
            tryAdvance();
            return createToken(RESERVED_OPERATORS, location, ">>=");
          default:
            return createToken(RESERVED_OPERATORS, location, ">>");
        }
      case '=':
        tryAdvance();
        return createToken(Terminal.GREATER_EQUALS, location, ">=");
      default:
        return createToken(Terminal.GREATER, location, ">");
    }
  }

  @Nullable
  private Token parseSlash() {
    assert current.tagged == '/';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(Terminal.DIVIDE, location, "/");
    }

    switch (current.tagged) {
      case '*':
        tryAdvance();
        parseCommentRest(location);
        return null;
      case '=':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "/=");
      default:
        return createToken(Terminal.DIVIDE, location, "/");
    }
  }

  private void parseCommentRest(Location location) {
    if (current != null) {
      byte last = current.tagged;
      while (tryAdvance()) {
        if (last == '*' && current.tagged == '/') {
          tryAdvance();
          return;
        }
        last = current.tagged;
      }
    }

    throw new LexerError(location, "Unclosed comment");
  }

  @NotNull
  private Token parseMinus() {
    assert current.tagged == '-';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(Terminal.MINUS, location, "-");
    }

    switch (current.tagged) {
      case '=':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "-=");
      case '-':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "--");
      default:
        return createToken(Terminal.MINUS, location, "-");
    }
  }

  @NotNull
  private Token parseLower() {
    assert current.tagged == '<';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(Terminal.LOWER, location, "<");
    }

    switch (current.tagged) {
      case '<':
        if (tryAdvance() && current.tagged == '=') {
          tryAdvance();
          return createToken(RESERVED_OPERATORS, location, "<<=");
        }

        return createToken(RESERVED_OPERATORS, location, "<<");
      case '=':
        tryAdvance();
        return createToken(Terminal.LOWER_EQUALS, location, "<=");
      default:
        return createToken(Terminal.LOWER, location, "<");
    }
  }

  @NotNull
  private Token parseEqual() {
    assert current.tagged == '=';

    Location location = current.loc;

    if (tryAdvance() && current.tagged == '=') {
      tryAdvance();
      return createToken(Terminal.EQUALS, location, "==");
    }

    return createToken(Terminal.EQUAL_SIGN, location, "=");
  }

  @NotNull
  private Token parseAnd() {
    assert current.tagged == '&';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(RESERVED_OPERATORS, location, "&");
    }

    switch (current.tagged) {
      case '&':
        tryAdvance();
        return createToken(Terminal.AND, location, "&&");
      case '=':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "&=");
      default:
        return createToken(RESERVED_OPERATORS, location, "&");
    }
  }

  @NotNull
  private Token parseStar() {
    assert current.tagged == '*';

    Location location = current.loc;

    if (tryAdvance() && current.tagged == '=') {
      tryAdvance();
      return createToken(RESERVED_OPERATORS, location, "*=");
    }

    return createToken(Terminal.MULTIPLY, location, "*");
  }

  @NotNull
  private Token parsePipe() {
    assert current.tagged == '|';

    Location location = current.loc;

    if (!tryAdvance()) {
      return createToken(RESERVED_OPERATORS, location, "|");
    }

    switch (current.tagged) {
      case '|':
        tryAdvance();
        return createToken(Terminal.OR, location, "||");
      case '=':
        tryAdvance();
        return createToken(RESERVED_OPERATORS, location, "|=");
      default:
        return createToken(RESERVED_OPERATORS, location, "|");
    }
  }

  @NotNull
  private Token parseIdent() {
    assert isAlphabet(current.tagged) || current.tagged == '_';

    Location location = current.loc;

    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(current.tagged);

    while (tryAdvance()
        && (isAlphabet(current.tagged)
            || current.tagged == '_'
            || Character.isDigit(current.tagged))) {
      builder.appendCodePoint(current.tagged);
    }

    return createToken(Terminal.IDENT, location, builder.toString());
  }

  @Contract(pure = true)
  private static boolean isAlphabet(byte c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  @Contract(pure = true)
  private static boolean isDigit(byte c) {
    return '0' <= c && c <= '9';
  }

  private Token createToken(Terminal terminal, Location location, String content) {
    int stringId = stringTable.getStringId(content);
    if (terminal != Terminal.IDENT) {
      return new Token(terminal, location, stringId, stringTable);
    }
    Terminal actualTerminal = Terminal.IDENT;
    if (keywordTerms.containsKey(stringId)) {
      actualTerminal = keywordTerms.get(stringId);
    }
    if (stringTable.isReservedIdentifier(stringId)) {
      actualTerminal = Terminal.RESERVED_IDENTIFIER;
    }
    return new Token(actualTerminal, location, stringId, stringTable);
  }

  public static List<Token> getAllTokens(String input) {
    return LexerUtils.getAllTokens(
        new SimpleLexer(new InputStreamIterator(new ByteArrayInputStream(input.getBytes()))));
  }
}
