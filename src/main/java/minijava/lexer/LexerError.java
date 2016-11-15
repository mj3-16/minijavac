package minijava.lexer;

import minijava.MJError;
import minijava.util.SourcePosition;

class LexerError extends MJError {

  LexerError(SourcePosition position, String message) {
    super("Lexer error at " + position + ":" + message);
  }
}
