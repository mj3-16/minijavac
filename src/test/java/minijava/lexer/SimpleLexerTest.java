package minijava.lexer;

import static minijava.token.Terminal.*;

import java.util.List;
import java.util.stream.Collectors;
import minijava.MJError;
import minijava.token.Terminal;
import minijava.token.Token;
import org.junit.Assert;
import org.junit.Test;

/** Lexer test cases */
public class SimpleLexerTest {

  @Test
  public void checkOutput() throws Exception {
    check("!", INVERT);
    check("catch", RESERVED_IDENTIFIER);
    check("const", RESERVED_IDENTIFIER);
    check("!=", array(UNEQUALS), array("!="));
    check("|=", RESERVED_OPERATORS);
    check("^", RESERVED_OPERATORS);
    check("%=", RESERVED_OPERATORS);
    check("_volatile", IDENT);
    check("|=", array(RESERVED_OPERATORS), array("|="));
    check("*abc", array(MULTIPLY, IDENT), array("*", "abc"));
  }

  @Test
  public void checkInvalid() throws Exception {
    fail("ä", "/*", "`", "–", "/**", "/** *d/", "/*/");
  }

  private void fail(String... inputs) {
    for (String input : inputs) {
      try {
        SimpleLexer.getAllTokens(input);
      } catch (MJError error) {
        continue;
      }
      Assert.fail(String.format("Didn't fail with input \"%s\"", input));
    }
  }

  private void check(String input, Terminal... expectedOutput) {
    List<Terminal> ret =
        SimpleLexer.getAllTokens(input).stream().map(t -> t.terminal).collect(Collectors.toList());
    ret.remove(EOF);
    Assert.assertArrayEquals("Input: " + input, ret.toArray(new Terminal[0]), expectedOutput);
  }

  private void check(String input, Terminal[] expectedTerminals, String[] expectedMatchedStrings) {
    List<Token> ret = SimpleLexer.getAllTokens(input);
    Terminal[] terminals = new Terminal[ret.size() - 1];
    String[] matchedStrings = new String[ret.size() - 1];
    for (int i = 0; i < terminals.length; i++) {
      terminals[i] = ret.get(i).terminal;
      matchedStrings[i] = ret.get(i).lexval;
    }
    String msg = String.format("Input \"%s\"", input);
    Assert.assertArrayEquals(msg, expectedTerminals, terminals);
    Assert.assertArrayEquals(msg, expectedMatchedStrings, matchedStrings);
  }

  private <T> T[] array(T... ts) {
    return ts;
  }
}
