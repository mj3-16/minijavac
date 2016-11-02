package minijava.parser;

import minijava.MJError;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;

class ParserError extends MJError {

  ParserError(Position position, String message) {
    super(String.format("Parser error at %s: %s", position, message));
  }

  ParserError(String rule, Terminal expectedTerminal, Token actualToken) {
    super(
        String.format(
            "Parser error at %s parsed via %s: expected %s but got %s",
            actualToken.position, rule, expectedTerminal, actualToken));
  }
}
