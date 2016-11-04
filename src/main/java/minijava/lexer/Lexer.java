package minijava.lexer;

import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import minijava.token.Token;

/** Basic interface of a lexer. */
public interface Lexer extends Iterator<Token> {

  /** Get the current token. */
  Token current();

  default Stream<Token> stream() {
    final Iterable<Token> iterable = () -> this;
    return StreamSupport.stream(iterable.spliterator(), false);
  }
}
