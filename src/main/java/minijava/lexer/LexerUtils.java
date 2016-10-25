package minijava.lexer;

import static minijava.lexer.Terminal.FALSE;
import static minijava.lexer.Terminal.NULL;
import static minijava.lexer.Terminal.TRUE;
import static minijava.lexer.Terminal.TerminalType.HIDDEN;
import static minijava.lexer.Terminal.TerminalType.OPERATOR;
import static minijava.lexer.Terminal.TerminalType.SYNTAX_ELEMENT;
import static minijava.lexer.Terminal.TerminalType.TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Lexer utility functions. */
public class LexerUtils {

  /** Returns the a list of all matched tokens in the form of the exercise sheet 2. */
  public static List<String> getTokenStrings(Lexer lexer) {
    List<String> strings = new ArrayList<>();
    for (Token token : getAllTokens(lexer)) {
      if (token.isType(HIDDEN)) { // ws or comments
        continue;
      }
      if (token.isEOF()) {
        strings.add("EOF");
      }
      if (token.isType(OPERATOR)
          || token.isType(SYNTAX_ELEMENT)
          || token.isType(TYPE)
          || token.isTerminal(NULL)
          || token.isTerminal(TRUE)
          || token.isTerminal(FALSE)) {
        strings.add(token.getContentString());
      }
      switch (token.getTerminal()) {
        case IDENT:
          strings.add("identifier " + token.getContentString());
          break;
        case INTEGER_LITERAL:
          strings.add("integer literal " + token.getContentString());
          break;
      }
    }
    return strings;
  }

  public static List<Token> getAllTokens(Lexer lexer) {
    return lexer.stream().collect(Collectors.toList());
  }
}
