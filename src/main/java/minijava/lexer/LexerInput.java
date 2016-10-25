package minijava.lexer;

import java.util.Iterator;

/** Input for a lexer (a stream of integers) it stops after the EOF character is read. */
public interface LexerInput extends Iterator<Integer> {

  /** Close the underlying streams and clean up if needed. */
  void close();

  Location getCurrentLocation();

  int current();

  default Location getLocation(int charId) {
    return new Location(0, 0);
  }

  default int getCurrentCharacterId() {
    return 0;
  }

  default boolean isCurrentChar(char... eitherChars) {
    for (char eitherChar : eitherChars) {
      if (eitherChar == current()) {
        return true;
      }
    }
    return false;
  }
}
