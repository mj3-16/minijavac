package minijava.lexer;

import java.util.Iterator;
import minijava.token.Position;

/** Input for a lexer (a stream of bytes) it stops after the EOF character is read. */
interface LexerInput extends Iterator<Byte> {

  /** Close the underlying streams and clean up if needed. */
  void close();

  Position getCurrentPosition();

  byte current();

  default boolean isCurrentChar(char... eitherChars) {
    for (char eitherChar : eitherChars) {
      if (eitherChar == current()) {
        return true;
      }
    }
    return false;
  }
}
