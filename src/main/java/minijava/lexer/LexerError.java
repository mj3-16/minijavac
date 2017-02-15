package minijava.lexer;

import minijava.MJError;
import minijava.utils.SourcePosition;

class LexerError extends MJError {

  LexerError(SourcePosition position, String message) {
    super("Lexer error at " + position + ":" + message);
  }
}
