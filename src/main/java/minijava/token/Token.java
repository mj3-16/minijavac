package minijava.token;

import static com.google.common.base.Preconditions.checkNotNull;
import static minijava.token.Terminal.Associativity;

import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/** Instances of this class are immutable. */
public class Token {

  public final Terminal terminal;
  public final Position position;
  public final String lexval;

  public Token(Terminal terminal, Position position, @Nullable String lexval) {
    this.terminal = checkNotNull(terminal);
    this.position = checkNotNull(position);
    this.lexval = lexval == null ? null : lexval.intern();
  }

  public boolean isOperator() {
    return terminal.associativity != null;
  }

  public Associativity associativity() {
    if (terminal.associativity == null) {
      throw new UnsupportedOperationException(terminal + " has no associativity");
    }
    return terminal.associativity;
  }

  public int precedence() {
    if (terminal.precedence == null) {
      throw new UnsupportedOperationException(terminal + " has no precedence");
    }
    return terminal.precedence;
  }

  public boolean isOneOf(Terminal... terminals) {
    return Arrays.stream(terminals).anyMatch(t -> t == this.terminal);
  }

  @Override
  public String toString() {
    switch (terminal) {
      case IDENT:
        return "identifier " + lexval;
      case INTEGER_LITERAL:
        return "integer literal " + lexval;
      case RESERVED:
        return lexval;
      case EOF:
        return "EOF";
      default:
        return terminal.string;
    }
  }
}
