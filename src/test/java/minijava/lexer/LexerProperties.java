package minijava.lexer;

import static minijava.token.Terminal.*;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.List;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.SourcePosition;
import minijava.util.SourceRange;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class LexerProperties {

  static final ImmutableSet<String> RESERVED_OPERATORS =
      ImmutableSet.of(
          "*=", "++", "+=", "-=", "--", "/=", "<<=", "<<", ">>=", ">>>=", ">>>", ">>", "%=", "&=",
          "&", "^=", "^", "~", "|=", "|");

  @Property(trials = 1000)
  public void prettyPrintingAndLexingTokenStreamIsIdentity(
      @Size(min = 0, max = 2000) List<@From(TokenGenerator.class) Token> tokens) {

    List<Token> expected =
        seq(tokens)
            .filter(t -> t.terminal != EOF)
            .append(new Token(EOF, SourceRange.FIRST_CHAR, null))
            .toList();

    String input = prettyPrint(expected);

    List<Token> actual = seq(new Lexer(input)).toList();

    // Some printfs for debugging:
    // System.out.println("input:    " + input);
    // System.out.println("expected: " + Iterables.toString(expected));
    // System.out.println("actual:   " + Iterables.toString(actual));

    // We can't directly compare Tokens because we haven't estimated source code locations.
    // Terminals and lexed strings have to match, though.

    // It should output the same terminal sequence:
    Assert.assertArrayEquals(
        seq(expected).map(t -> t.terminal).toArray(), seq(actual).map(t -> t.terminal).toArray());

    // It should also (maybe?) output the same sequence of strings. Not sure about how long this holds, though
    Assert.assertArrayEquals(
        seq(expected).map(t -> t.lexval).toArray(), seq(actual).map(t -> t.lexval).toArray());

    // SourceRanges should be useful, e.g. to exactly determine where the token came from
    StringBuilder sb = new StringBuilder();
    SourcePosition pos = new SourcePosition(1, 0);
    int i = 0;
    for (char c : Lists.charactersOf(input)) {
      if (i >= actual.size()) {
        break;
      }
      Token t = actual.get(i);

      if (t.isOneOf(EOF)) {
        // has no real source code representation
        break;
      }

      if (pos.compareTo(t.range.end) >= 0) {
        assert pos.equals(t.range.end);
        String lookedUp = sb.toString();
        String repr = t.terminal.hasLexval() ? t.lexval : t.terminal.string;
        // Some printfs for debugging:
        // System.out.println("repr:     " + repr);
        // System.out.println("lookedUp: " + lookedUp);
        Assert.assertEquals(
            "Lookup via SourceRange should yield the proper String representation", repr, lookedUp);
        sb = new StringBuilder();
        i++;
        if (i >= actual.size()) {
          break;
        }
        t = actual.get(i);
      }

      if (pos.compareTo(t.range.begin) >= 0) {
        sb.append(c);
      }

      if (c == '\n') {
        pos = new SourcePosition(pos.line + 1, 0);
      } else {
        pos = new SourcePosition(pos.line, pos.column + 1);
      }
    }
  }

  /**
   * Convert a token stream into a string from which that token stream might have originated (modulo
   * mandatory separating spaces).
   *
   * @param tokens A token stream we want to pretty print, so that we can parse it again.
   * @return a pretty printed string of the token stream
   */
  private String prettyPrint(Iterable<Token> tokens) {
    char last = ' '; // so that we don't start with a space
    Token prev = new Token(Terminal.EOF, SourceRange.FIRST_CHAR, null);

    StringBuilder sb = new StringBuilder(4096);
    for (Token t : tokens) {
      String text = t.lexval != null ? t.lexval : t.terminal.string;
      if (t.terminal != EOF && text.length() > 0) {
        if (Character.isJavaIdentifierPart(text.charAt(0)) && Character.isJavaIdentifierPart(last)
            || isOperator(t) && isOperator(prev)) {
          // Consider an identifier directly following an identifier.
          // In this case we have to insert a space for separation.
          // In the general case, we want to preserve what's in tokens!
          // e.g. identifiers may directly be followed by operators.
          sb.append(' ');
        }
        sb.append(text);
        last = text.charAt(text.length() - 1);
        prev = t;
      }
    }
    return sb.toString();
  }

  private static boolean isOperator(Token t) {
    return t.isOperator() || (t.terminal == RESERVED && RESERVED_OPERATORS.contains(t.lexval));
  }
}
