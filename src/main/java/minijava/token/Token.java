package minijava.token;

/** Instances of this class are immutable. */
public class Token {

  public final Terminal terminal;
  public final Position position;
  public final String lexval;

  public Token(Terminal terminal, Position position, String lexval) {
    this.terminal = terminal;
    this.position = position;
    this.lexval = lexval;
  }

  public boolean isTerminal(Terminal otherTerminal) {
    return terminal.equals(otherTerminal);
  }

  public boolean isEOF() {
    return terminal.equals(Terminal.EOF);
  }

  public boolean isType(Terminal.TerminalType type) {
    return terminal.isType(type);
  }

  @Override
  public String toString() {
    return terminal.getDescription() + position.toString() + "(" + lexval + ")";
  }

  /** Returns a string that only consists of the terminal description belonging to this token. */
  public String toSimpleString() {
    return terminal.getDescription();
  }
}
