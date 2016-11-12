package minijava.parser;

import java.util.List;
import minijava.token.Terminal;

class TerminalStream {
  final List<Terminal> terminals;

  TerminalStream(List<Terminal> terminals) {
    this.terminals = terminals;
  }
}
