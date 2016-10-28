package minijava.lexer;

import static minijava.lexer.Terminal.RESERVED_OPERATORS;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.MJError;

/** SLL(1) parser style lexer implementation. */
public class SimpleLexer implements Lexer {

  private LexerInput input;
  private StringTable stringTable = new StringTable();
  private Token current = null;
  private List<Character> characters = new ArrayList<>();
  private Location location;
  private List<Token> lookAheadBuffer = new ArrayList<>();
  private Map<Integer, Terminal> keywordTerms = new HashMap<>();
  private int numberOfEOFs = 0;

  public SimpleLexer(LexerInput input) {
    this.input = input;
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

  private Token parseNextToken() {
    if (input.current() > 127) {
      throw new LexerError(
          input.getCurrentLocation(), String.format("Invalid char number %d", input.current()));
    }
    if (input.current() <= 0) {
      location = input.getCurrentLocation();
      return createToken(Terminal.EOF, "");
    }
    omitWS();
    if (input.current() <= 0) {
      location = input.getCurrentLocation();
      return createToken(Terminal.EOF, "");
    }
    location = input.getCurrentLocation();
    byte cur = (byte) input.current();
    if (Character.isDigit(cur)) {
      return parseInt();
    }
    if (isAlphabet(cur) || cur == '_') {
      return parseIdent();
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
        return createToken(Terminal.LPAREN, "(");
      case ')':
        input.next();
        return createToken(Terminal.RPAREN, ")");
      case '?':
        input.next();
        return createToken(Terminal.QUESTION_MARK, "?");
      case ';':
        input.next();
        return createToken(Terminal.SEMICOLON, ";");
      case '[':
        input.next();
        return createToken(Terminal.LBRACKET, "[");
      case ']':
        input.next();
        return createToken(Terminal.RBRACKET, "]");
      case '/':
        input.next();
        Token token = parseSlash();
        if (token != null) {
          return token;
        }
        return parseNextToken();
      case '-':
        input.next();
        return parseMinus();
      case '{':
        input.next();
        return createToken(Terminal.LCURLY, "{");
      case '}':
        input.next();
        return createToken(Terminal.RCURLY, "}");
      case ':':
        input.next();
        return createToken(Terminal.COLON, ":");
      case ',':
        input.next();
        return createToken(Terminal.COMMA, ",");
      case '%':
        input.next();
        return parseModulo();
      case '.':
        input.next();
        return createToken(Terminal.DOT, ".");
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
        return createToken(RESERVED_OPERATORS, "~");
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
        input.getCurrentLocation(),
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
        return createToken(Terminal.UNEQUALS, "!=");
      default:
        return createToken(Terminal.INVERT, "!");
    }
  }

  private Token parseCaret() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "^=");
      default:
        return createToken(RESERVED_OPERATORS, "^");
    }
  }

  private Token parseModulo() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(Terminal.RESERVED_OPERATORS, "%=");
      default:
        return createToken(Terminal.MODULO, "%");
    }
  }

  private Token parseInt() {
    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(input.current());
    if (input.current() == '0') {
      input.next();
      byte cur = input.current();
      while (cur >= '1' && cur <= '9') {
        builder.appendCodePoint(cur);
        cur = input.next();
      }
    } else {
      input.next();
      byte cur = input.current();
      while (cur >= '0' && cur <= '9') {
        builder.appendCodePoint(cur);
        cur = input.next();
      }
    }
    return createToken(Terminal.INTEGER_LITERAL, builder.toString());
  }

  private Token parsePlus() {
    switch (input.current()) {
      case '+':
        input.next();
        return createToken(RESERVED_OPERATORS, "++");
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "+=");
      default:
        return createToken(Terminal.PLUS, "+");
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
                return createToken(RESERVED_OPERATORS, ">>>=");
              default:
                return createToken(RESERVED_OPERATORS, ">>>");
            }
          case '=':
            input.next();
            return createToken(RESERVED_OPERATORS, ">>=");
          default:
            return createToken(RESERVED_OPERATORS, ">>");
        }
      case '=':
        input.next();
        return createToken(Terminal.GREATER_EQUALS, ">=");
      default:
        return createToken(Terminal.GREATER, ">");
    }
  }

  private Token parseSlash() {
    switch (input.current()) {
      case '*':
        input.next();
        parseCommentRest();
        return null;
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "/=");
      default:
        return createToken(Terminal.DIVIDE, "/");
    }
  }

  private void parseCommentRest() {
    while (true) {
      byte cur = input.current();
      byte next = input.next();
      if (cur <= 0 || next <= 0) {
        throw createError();
      }
      if (cur == '*' && next == '/') {
        input.next();
        break;
      }
    }
  }

  private Token parseMinus() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "-=");
      case '-':
        input.next();
        return createToken(RESERVED_OPERATORS, "--");
      default:
        return createToken(Terminal.MINUS, "-");
    }
  }

  private Token parseLower() {
    switch (input.current()) {
      case '<':
        input.next();
        switch (input.current()) {
          case '=':
            input.next();
            return createToken(RESERVED_OPERATORS, "<<=");
          default:
            return createToken(RESERVED_OPERATORS, "<<");
        }
      case '=':
        input.next();
        return createToken(Terminal.LOWER_EQUALS, "<=");
      default:
        return createToken(Terminal.LOWER, "<");
    }
  }

  private Token parseEqual() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(Terminal.EQUALS, "==");
      default:
        return createToken(Terminal.EQUAL_SIGN, "=");
    }
  }

  private Token parseAnd() {
    switch (input.current()) {
      case '&':
        input.next();
        return createToken(Terminal.AND, "&&");
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "&=");
      default:
        return createToken(RESERVED_OPERATORS, "&");
    }
  }

  private Token parseStar() {
    switch (input.current()) {
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "*=");
      default:
        return createToken(Terminal.MULTIPLY, "*");
    }
  }

  private Token parsePipe() {
    switch (input.current()) {
      case '|':
        input.next();
        return createToken(Terminal.OR, "||");
      case '=':
        input.next();
        return createToken(RESERVED_OPERATORS, "|=");
      default:
        return createToken(RESERVED_OPERATORS, "|");
    }
  }

  private Token parseIdent() {
    StringBuilder builder = new StringBuilder();
    builder.appendCodePoint(input.current());
    byte cur = (byte) (int) input.next();
    while (input.current() != -1 && (isAlphabet(cur) || cur == '_' || Character.isDigit(cur))) {
      builder.appendCodePoint(cur);
      cur = (byte) (int) input.next();
    }
    return createToken(Terminal.IDENT, builder.toString());
  }

  private boolean isAlphabet(byte c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  private Token createToken(Terminal terminal, String content) {
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

  private Token nextToken() {
    if (current != null && current.isEOF()) {
      return current;
    }
    if (lookAheadBuffer.size() > 0) {
      current = lookAheadBuffer.get(0);
      lookAheadBuffer.remove(0);
    } else {
      current = parseNextToken();
    }
    return current;
  }

  @Override
  public Token current() {
    if (current == null) {
      current = parseNextToken();
      if (current.isEOF()) {
        numberOfEOFs++;
      }
    }
    return current;
  }

  @Override
  public Token lookAhead(int lookAhead) {
    if (lookAhead <= 0) {
      return current();
    }
    if (current().isEOF()) {
      return current();
    }
    Token curToken = current();
    while (lookAheadBuffer.size() < lookAhead && !curToken.isEOF()) {
      curToken = parseNextToken();
      lookAheadBuffer.add(curToken);
    }
    if (lookAheadBuffer.size() >= lookAhead) {
      return lookAheadBuffer.get(lookAhead - 1);
    } else {
      return lookAheadBuffer.get(lookAheadBuffer.size() - 1);
    }
  }

  @Override
  public StringTable getStringTable() {
    return stringTable;
  }

  @Override
  public boolean hasNext() {
    return numberOfEOFs < 1;
  }

  @Override
  public Token next() {
    current = nextToken();
    if (!lookAheadBuffer.isEmpty()) {
      lookAheadBuffer.remove(0);
    }
    if (current.isEOF()) {
      numberOfEOFs++;
    }
    return current;
  }

  public static List<Token> getAllTokens(String input) {
    return LexerUtils.getAllTokens(
        new SimpleLexer(new BasicLexerInput(new ByteArrayInputStream(input.getBytes()))));
  }
}
