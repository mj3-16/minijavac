package minijava.lexer;

import com.google.common.collect.Sets;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import java.util.Set;
import java.util.stream.IntStream;
import minijava.token.Terminal;
import minijava.token.Token;
import minijava.util.SourceRange;
import org.jooq.lambda.Seq;

/**
 * Generates a random @Token@, with random content. Also makes sure that the content is actually
 * lexed as the corresponding @Terminal@.
 */
public class TokenGenerator extends Generator<Token> {

  public TokenGenerator() {
    super(Token.class);
  }

  @Override
  public Token generate(SourceOfRandomness random, GenerationStatus status) {
    Terminal terminal = random.choose(Terminal.values());
    switch (terminal) {
      case IDENT:
      case INTEGER_LITERAL:
      case RESERVED:
        String lexval = generateStringForTerminal(terminal, random);
        return new Token(terminal, SourceRange.FIRST_CHAR, lexval);
      default:
        return new Token(terminal, SourceRange.FIRST_CHAR, null);
    }
  }

  public static String generateStringForTerminal(Terminal t, SourceOfRandomness random) {
    switch (t) {
      case IDENT:
        return generateIdentifier(random);
      case INTEGER_LITERAL:
        return random.nextBigInteger(random.nextInt(1, 64)).abs().toString();
      case RESERVED:
        return random.choose(
            Sets.union(LexerProperties.RESERVED_OPERATORS, Lexer.RESERVED_IDENTIFIERS));
      default:
        return t.string;
    }
  }

  private static String generateIdentifier(SourceOfRandomness random) {
    StringBuilder id;
    do {
      id = new StringBuilder();
      Seq.of(random.choose(IDENT_FIRST_CHAR))
          .concat(
              Seq.generate(() -> random.choose(IDENT_FOLLOWING_CHARS)).limit(random.nextInt(1, 30)))
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
