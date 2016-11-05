package minijava.token;

import static minijava.token.Terminal.*;

import java.util.Arrays;
import org.jetbrains.annotations.Nullable;

/** Instances of this class are immutable. */
public class Token {

  public final Terminal terminal;
  public final Position position;
  public final String lexval;

  public Token(Terminal terminal, Position position, @Nullable String lexval) {
    this.terminal = terminal;
    this.position = position;
    this.lexval = lexval == null ? null : lexval.intern();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(position.toString());
    sb.append(" ");
    switch (terminal) {
      case IDENT:
        sb.append("identifier (");
        sb.append(lexval);
        sb.append(")");
        break;
      case INTEGER_LITERAL:
        sb.append("integer literal (");
        sb.append(lexval);
        sb.append(")");
        break;
      case RESERVED:
        sb.append(lexval);
        break;
      case EOF:
        sb.append("EOF");
        break;
      default:
        sb.append(terminal.string.get());
    }
    return sb.toString();
  }

  public boolean isOneOf(Terminal... terminals) {
    return Arrays.stream(terminals).anyMatch(t -> terminal == t);
  }

  public boolean isOperator() {
    return terminal.precedence.isPresent();
  }

  public int precedence() {
    if (!terminal.precedence.isPresent()) {
      throw new UnsupportedOperationException(terminal + " has no precedence");
    }
    return terminal.precedence.get();
  }

  public Associativity associativity() {
    if (!terminal.associativity.isPresent()) {
      throw new UnsupportedOperationException(terminal + " has no associativity");
    }
    return terminal.associativity.get();
  }
}
