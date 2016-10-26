package minijava.lexer;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static minijava.lexer.Terminal.EOF;
import static minijava.lexer.Terminal.IDENT;
import static minijava.lexer.Terminal.INVERT;
import static minijava.lexer.Terminal.MULTIPLY;
import static minijava.lexer.Terminal.RESERVED_IDENTIFIER;
import static minijava.lexer.Terminal.RESERVED_OPERATORS;

/** Lexer test cases */
public class SimpleLexerTest {

  @Test
  public void checkOutput() throws Exception {
    check("!", INVERT);
    check("catch", RESERVED_IDENTIFIER);
    check("const", RESERVED_IDENTIFIER);
    check("!=", array(RESERVED_OPERATORS), array("!="));
    check("|=", RESERVED_OPERATORS);
    check("^", RESERVED_OPERATORS);
    check("%=", RESERVED_OPERATORS);
    check("_volatile", IDENT);
    check("|=", array(RESERVED_OPERATORS), array("|="));
    check("*abc", array(MULTIPLY, IDENT), array("*", "abc"));
  }

  private void check(String input, Terminal... expectedOutput) {
    List<Terminal> ret =
        SimpleLexer.getAllTokens(input)
            .stream()
            .map(Token::getTerminal)
            .collect(Collectors.toList());
    ret.remove(EOF);
    Assert.assertArrayEquals("Input: " + input, ret.toArray(new Terminal[0]), expectedOutput);
  }

  private void check(String input, Terminal[] expectedTerminals, String[] expectedMatchedStrings) {
    List<Token> ret = SimpleLexer.getAllTokens(input);
    Terminal[] terminals = new Terminal[ret.size() - 1];
    String[] matchedStrings = new String[ret.size() - 1];
    for (int i = 0; i < terminals.length; i++) {
      terminals[i] = ret.get(i).getTerminal();
      matchedStrings[i] = ret.get(i).getContentString();
    }
    String msg = String.format("Input \"%s\"", input);
    Assert.assertArrayEquals(msg, expectedTerminals, terminals);
    Assert.assertArrayEquals(msg, expectedMatchedStrings, matchedStrings);
  }

  private <T> T[] array(T... ts) {
    return ts;
  }
}
