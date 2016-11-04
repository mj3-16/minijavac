package minijava.parser;

import java.util.Arrays;
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

  ParserError(String rule, Terminal expectedTerminal, String expectedValue, Token actualToken) {
    super(
        String.format(
            "Parser error at %s parsed via %s: expected %s with value '%s' but got %s",
            actualToken.position, rule, expectedTerminal, expectedValue, actualToken));
  }

  ParserError(String rule, Token unexpectedToken, Terminal[] expectedTerminals) {
    super(
        String.format(
            "Parser error at %s parsed via %s: unexpected %s with value '%s' expected one of %s",
            unexpectedToken.position,
            rule,
            unexpectedToken.terminal,
            unexpectedToken.lexval,
            Arrays.toString(expectedTerminals)));
  }
}
