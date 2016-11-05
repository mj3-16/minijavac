package minijava.lexer;

import static minijava.token.Terminal.*;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import minijava.token.Position;
import minijava.token.Terminal;
import minijava.token.Token;
import org.jooq.lambda.Seq;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class LexerProperties {

  private static final ImmutableSet<String> RESERVED_OPERATORS =
      ImmutableSet.of(
          "*=", "++", "+=", "-=", "--", "/=", "<<=", "<<", ">>=", ">>>=", ">>>", ">>", "%=", "&=",
          "&", "^=", "^", "~", "|=", "|");

  @Property(trials = 1000)
  public void prettyPrintingAndLexingTokenStreamIsIdentity(
      @Size(min = 0, max = 2000) List<@From(TokenGenerator.class) Token> tokens) {

    List<Token> expected =
        seq(tokens)
            .filter(t -> t.terminal != EOF)
            .append(new Token(EOF, new Position(0, 0), null))
            .toList();

    String input = prettyPrint(expected);

    List<Token> actual = seq(Lexer.getAllTokens(input)).toList();

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
    Token prev = new Token(Terminal.EOF, new Position(0, 0), null);

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
        maybeAppendWhitespace(sb);
        maybeAppendComment(sb);
        last = text.charAt(text.length() - 1);
        prev = t;
      }
    }
    return sb.toString();
  }

  private static boolean isOperator(Token t) {
    return t.isOperator() || (t.terminal == RESERVED && RESERVED_OPERATORS.contains(t.lexval));
  }

  private void maybeAppendWhitespace(StringBuilder sb) {
    // TODO
    // Seq.generate(() -> random.choose(WHITESPACE)).limit(random.nextInt(1, 5)).toList());

  }

  private void maybeAppendComment(StringBuilder sb) {
    // TODO
    // Character[] choice =
    //              Seq.of(WHITESPACE).concat(Seq.of(IDENT_FOLLOWING_CHARS)).toArray(Character[]::new);
    // asString(Seq.generate(() -> random.choose(choice)).limit(random.nextInt(1, 30)).toList())
  }

  /**
   * Generates a random @Token@, with random content. Also makes sure that the content is actually
   * lexed as the corresponding @Terminal@.
   */
  public static class TokenGenerator extends Generator<Token> {

    public TokenGenerator() {
      super(Token.class);
    }

    @Override
    public Token generate(SourceOfRandomness random, GenerationStatus status) {
      Terminal terminal = random.choose(Terminal.values());
      switch (terminal) {
        case IDENT:
          return new Token(terminal, new Position(0, 0), generateIdentifier(random));
        case INTEGER_LITERAL:
          return new Token(
              terminal,
              new Position(0, 0),
              random.nextBigInteger(random.nextInt(1, 64)).abs().toString());
        case RESERVED:
          return new Token(
              terminal,
              new Position(0, 0),
              random.choose(Sets.union(RESERVED_OPERATORS, Lexer.RESERVED_IDENTIFIERS)));
        default:
          return new Token(terminal, new Position(0, 0), null);
      }
    }

    private static String generateIdentifier(SourceOfRandomness random) {
      StringBuilder id;
      do {
        id = new StringBuilder();
        Seq.of(random.choose(IDENT_FIRST_CHAR))
            .concat(
                Seq.generate(() -> random.choose(IDENT_FOLLOWING_CHARS))
                    .limit(random.nextInt(1, 30)))
            .forEach(id::append);
      } while (ALL_KEYWORDS.contains(id.toString()));
      return id.toString();
    }

    private static final Character[] IDENT_FIRST_CHAR =
        IntStream.concat(
                IntStream.of('_'),
                IntStream.concat(IntStream.rangeClosed('A', 'Z'), IntStream.rangeClosed('a', 'z')))
            .mapToObj(c -> (char) c)
            .toArray(Character[]::new);

    private static final Character[] IDENT_FOLLOWING_CHARS =
        IntStream.concat(
                IntStream.of('_'),
                IntStream.concat(
                    IntStream.rangeClosed('A', 'Z'),
                    IntStream.concat(
                        IntStream.rangeClosed('a', 'z'), IntStream.rangeClosed('0', '9'))))
            .mapToObj(c -> (char) c)
            .toArray(Character[]::new);

    private static final Set<String> ALL_KEYWORDS =
        Sets.union(Lexer.RESERVED_IDENTIFIERS, Lexer.KEYWORDS.keySet());
  }
}
