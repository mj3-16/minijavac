package minijava.parser;

import java.util.Arrays;
import minijava.MJError;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.SourceRange;

class ParserError extends MJError {

  ParserError(SourceRange range, String message) {
    super(String.format("Parser error at %s: %s", range, message));
  }

  ParserError(String rule, Terminal expectedTerminal, Token actualToken) {
    super(
        String.format(
            "Parser error at %s parsed via %s: expected %s but got %s",
            actualToken.range, rule, expectedTerminal, actualToken));
  }

  ParserError(String rule, Terminal expectedTerminal, String expectedValue, Token actualToken) {
    super(
        String.format(
            "Parser error at %s parsed via %s: expected %s with value '%s' but got %s",
            actualToken.range, rule, expectedTerminal, expectedValue, actualToken));
  }

  ParserError(String rule, Token unexpectedToken, Terminal[] expectedTerminals) {
    super(
        String.format(
            "Parser error at %s parsed via %s: unexpected %s with value '%s' expected one of %s",
            unexpectedToken.range,
            rule,
            unexpectedToken.terminal,
            unexpectedToken.lexval,
            Arrays.toString(expectedTerminals)));
  }
}
