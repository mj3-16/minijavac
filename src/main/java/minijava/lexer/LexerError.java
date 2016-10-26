package minijava.lexer;

import minijava.MJError;

class LexerError extends MJError {

  LexerError(Location location, String message) {
    super("Lexer error at " + location + ":" + message);
  }
}
