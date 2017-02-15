package minijava.parser;

import java.util.Arrays;
import java.util.List;
import minijava.MJError;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.utils.SourceRange;

class ParserError extends MJError {

  public final SourceRange range;

  ParserError(SourceRange range, String message) {
    super(String.format("Parser error at %s: %s", range, message));
    this.range = range;
  }

  ParserError(String rule, Terminal expectedTerminal, Token actualToken) {
    super(
        String.format(
            "Parser error at %s parsed via %s: expected %s but got %s",
            actualToken.range(), rule, expectedTerminal, actualToken));
    this.range = actualToken.range();
  }

  ParserError(String rule, Terminal expectedTerminal, String expectedValue, Token actualToken) {
    super(
        String.format(
            "Parser error at %s parsed via %s: expected %s with value '%s' but got %s",
            actualToken.range(), rule, expectedTerminal, expectedValue, actualToken));
    this.range = actualToken.range();
  }

  ParserError(String rule, Token unexpectedToken, Terminal[] expectedTerminals) {
    super(
        String.format(
            "Parser error at %s parsed via %s: unexpected %s with value '%s' expected one of %s",
            unexpectedToken.range(),
            rule,
            unexpectedToken.terminal,
            unexpectedToken.lexval,
            Arrays.toString(expectedTerminals)));
    this.range = unexpectedToken.range();
  }

  @Override
  public String getSourceReferencingMessage(List<String> sourceFile) {
    return getMessage()
        + System.lineSeparator()
        + System.lineSeparator()
        + range.annotateSourceFileExcerpt(sourceFile);
  }
}
