package minijava.lexer;

import minijava.MJError;

public class LexerError extends MJError {

  public LexerError(Location location, String message) {
    super("Lexer error at " + location + ":" + message);
  }
}
