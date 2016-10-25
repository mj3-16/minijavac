package minijava.lexer;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Basic interface of a lexer. */
public interface Lexer extends Iterator<Token> {

  /** Get the current token. */
  Token current();

  /** Get the n.th next token. */
  Token lookAhead(int lookAhead);

  StringTable getStringTable();

  default Stream<Token> stream() {
    final Iterable<Token> iterable = () -> this;
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}
