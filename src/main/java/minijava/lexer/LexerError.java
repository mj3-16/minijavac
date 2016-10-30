package minijava.lexer;

import minijava.MJError;
import minijava.token.Position;

class LexerError extends MJError {

  LexerError(Position position, String message) {
    super("Lexer error at " + position + ":" + message);
  }
}
