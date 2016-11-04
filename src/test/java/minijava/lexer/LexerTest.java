package minijava.lexer;

import static minijava.token.Terminal.*;

import com.google.common.math.IntMath;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import minijava.MJError;
import minijava.token.Terminal;
import minijava.token.Token;
import org.junit.Assert;
import org.junit.Test;

/** Lexer test cases */
public class LexerTest {

  @Test
  public void lexManyConsecutiveComments_noStackoverflowOccurs() {
    ByteBuffer input = ByteBuffer.allocate(16 * IntMath.pow(2, 20)); // 16 MiB
    byte[] comment = "/**/".getBytes(StandardCharsets.US_ASCII);
    while (input.limit() - input.position() >= comment.length) {
      input.put(comment);
    }
    Lexer lexer = new Lexer(new BasicLexerInput(new ByteArrayInputStream(input.array())));
    while (lexer.hasNext()) {
      lexer.next();
    }
  }

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
        Lexer.getAllTokens(input);
      } catch (MJError error) {
        continue;
      }
      Assert.fail(String.format("Didn't fail with input \"%s\"", input));
    }
  }

  private void check(String input, Terminal... expectedOutput) {
    List<Terminal> ret =
        Lexer.getAllTokens(input).stream().map(t -> t.terminal).collect(Collectors.toList());
    ret.remove(EOF);
    Assert.assertArrayEquals("Input: " + input, ret.toArray(new Terminal[0]), expectedOutput);
  }

  private void check(String input, Terminal[] expectedTerminals, String[] expectedMatchedStrings) {
    List<Token> ret = Lexer.getAllTokens(input);
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
