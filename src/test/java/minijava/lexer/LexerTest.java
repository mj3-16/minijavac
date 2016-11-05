package minijava.lexer;

import static minijava.token.Terminal.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.math.IntMath;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import minijava.MJError;
import minijava.token.Terminal;
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
    Lexer lexer = new Lexer(new ByteArrayInputStream(input.array()));
    while (lexer.hasNext()) {
      lexer.next();
    }
  }

  @Test
  public void lexInvalidInput_throwsException() {
    String[] inputs = {"ä", "/*", "`", "–", "/**", "/** *d/", "/*/"};
    for (String input : inputs) {
      try {
        Lexer l = new Lexer(input);
        while (l.hasNext()) {
          l.next();
        }
        ;
      } catch (MJError e) {
        continue;
      }
      Assert.fail(String.format("Didn't fail with input '%s'", input));
    }
  }

  @Test
  public void lexValidInput_expectedTokensEmitted() throws Exception {
    ImmutableMultimap<String, Terminal> inputAndOutput =
        new ImmutableMultimap.Builder<String, Terminal>()
            .put("!", NOT)
            .put("catch", RESERVED)
            .put("const", RESERVED)
            .put("!=", NEQ)
            .put("|=", RESERVED)
            .put("^=", RESERVED)
            .put("%=", RESERVED)
            .put("_volatile", IDENT)
            .putAll("*abc", MUL, IDENT)
            .build();

    for (Map.Entry<String, Collection<Terminal>> e : inputAndOutput.asMap().entrySet()) {
      Lexer lexer = new Lexer(e.getKey());
      List<Terminal> actual =
          ImmutableList.copyOf(lexer).stream().map(t -> t.terminal).collect(Collectors.toList());
      actual.remove(EOF);
      Assert.assertEquals(e.getValue(), actual);
    }
  }
}
