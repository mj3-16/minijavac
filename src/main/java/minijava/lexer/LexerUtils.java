package minijava.lexer;

import java.util.List;
import java.util.stream.Collectors;
import minijava.token.Token;

/** Lexer utility functions. */
public class LexerUtils {

  public static List<Token> getAllTokens(Lexer lexer) {
    return lexer.stream().collect(Collectors.toList());
  }
}
