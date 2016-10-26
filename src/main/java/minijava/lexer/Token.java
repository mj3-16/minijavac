package minijava.lexer;

/** Lexer token */
public class Token {

  private final Terminal terminal;
  private final Location location;
  private final int content;
  private final StringTable stringTable;

  Token(Terminal terminal, Location location, int content, StringTable stringTable) {
    this.terminal = terminal;
    this.location = location;
    this.content = content;
    this.stringTable = stringTable;
  }

  public int getContent() {
    return content;
  }

  public String getContentString() {
    return stringTable.getString(content);
  }

  public Location getLocation() {
    return location;
  }

  public StringTable getStringTable() {
    return stringTable;
  }

  public Terminal getTerminal() {
    return terminal;
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
    return terminal.getDescription() + location.toString() + "(" + getContentString() + ")";
  }

  /** Returns a string that only consists of the terminal description belonging to this token. */
  public String toSimpleString() {
    return terminal.getDescription();
  }
}
