package minijava.lexer;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Chars;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.generator.java.util.ListGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;

import java.io.*;
import java.util.*;
import java.util.stream.IntStream;

import org.apache.commons.io.IOUtils;
import org.jooq.lambda.Seq;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class SimpleLexerProperties {
  private static List<Byte> asByteStream(String s) {
    return Bytes.asList(s.getBytes());
  }

  @Property(trials = 1000)
  public void prettyPrintingAndLexingTokenStreamIsIdentity(
      @Size(min = 0, max = 2000) List<@From(TokenGenerator.class) Token> tokens) {
    StringTable strings = new StringTable();
    List<Terminal> notWanted = Arrays.asList(Terminal.EOF, Terminal.COMMENT, Terminal.WS);
    List<Token> expected =
        seq(tokens)
            .filter(t -> !notWanted.contains(t.getTerminal()))
            .append(new Token(Terminal.EOF, new Location(0,0), strings.addString(""), strings))
            .toList();

    String input = prettyPrint(expected);
    List<Token> actual = seq(SimpleLexer.getAllTokens(input)).toList();

    // Some printfs for debugging:
    // System.out.println("input:    " + input);
    // System.out.println("expected: " + Iterables.toString(expected));
    // System.out.println("actual:   " + Iterables.toString(actual));

    // It should output the same terminal sequence:
    Assert.assertArrayEquals(seq(expected).map(Token::getTerminal).toArray(), seq(actual).map(Token::getTerminal).toArray());

    // It should also (maybe?) output the same sequence of strings. Not sure about how long this holds, though
    Assert.assertArrayEquals(seq(expected).<String>map(Token::getContentString).toArray(), seq(actual).<String>map(Token::getContentString).toArray());
  }

  private String prettyPrint(Iterable<Token> tokens) {
    char last = ' '; // so that we don't start with a space
    Terminal term = Terminal.EOF;

    StringBuilder sb = new StringBuilder(4096);
    for (Token t : tokens) {
      String text = t.getContentString();
      if (text.length() > 0) {
        if (Character.isJavaIdentifierPart(text.charAt(0))
          && Character.isJavaIdentifierPart(last)
          || t.isType(Terminal.TerminalType.OPERATOR)
          && term.isType(Terminal.TerminalType.OPERATOR)) {
          sb.append(' ');
        }
        sb.append(text);
        last = text.charAt(text.length() - 1);
        term = t.getTerminal();
      }
    }
    return sb.toString();
  }

  public static class TokenGenerator extends Generator<Token> {
    private final StringTable strings = new StringTable();

    public TokenGenerator() {
      super(Token.class);
    }

    @Override
    public Token generate(SourceOfRandomness random, GenerationStatus status) {
      Terminal[] terminals = Terminal.values();
      Terminal terminal = random.choose(terminals);
      int content = strings.addString(generateString(terminal, random));
      return new Token(terminal, new Location(0,0), content, strings);
    }

    private static String generateString(Terminal t, SourceOfRandomness random) {
      switch (t) {
        case EOF:
          return "";
        case COMMENT:
          Character[] choice = Seq.of(WHITESPACE).concat(Seq.of(IDENT_FOLLOWING_CHARS)).toArray(Character[]::new);
          return "/*" +
                  asString(Seq.generate(() -> random.choose(choice))
                          .limit(random.nextInt(1, 30))
                          .toList()) +
                  "*/";
        case WS:
          return asString(
                  Seq.generate(() -> random.choose(WHITESPACE))
                    .limit(random.nextInt(1, 5))
                    .toList());
        case INTEGER_LITERAL:
          return random.nextBigInteger(random.nextInt(1, 64)).abs().toString();
        case IDENT:
          while (true) {
            String id = asString(Seq.of(random.choose(IDENT_FIRST_CHAR))
                    .concat(Seq.generate(() -> random.choose(IDENT_FOLLOWING_CHARS)))
                    .limit(random.nextInt(1, 30))
                    .toList());
            if (!Arrays.asList(KEYWORDS).contains(id)) {
              // In this case we haven't generated a keyword and are good to go.
              return id;
            }
          }
        case RESERVED_OPERATORS:
          return random.choose(RESERVED_OPS);
        case RESERVED_IDENTIFIER:
          return random.choose(RESERVED_IDS);
        default:
          return t.getDescription();
      }
    }

    private static String asString(Collection<Character> chars) {
      return new String(Chars.toArray(chars));
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

    private static final String[] RESERVED_OPS = {
            "*=",
            "++",
            "+=",
            "-=",
            "--",
            "/=",
            "<<=",
            "<<",
            ">>=",
            ">>>=",
            ">>>",
            ">>",
            "%=",
            "&=",
            "&",
            "^=",
            "^",
            "~",
            "|=",
            "|",
    };

    private static final String[] RESERVED_IDS = {
            "abstract",
            "assert",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "const",
            "continue",
            "default",
            "double",
            "do",
            "enum",
            "extends",
            "finally",
            "final",
            "float",
            "for",
            "goto",
            "implements",
            "import",
            "instanceof",
            "interface",
            "long",
            "native",
            "package",
            "private",
            "protected",
            "short",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "throws",
            "throw",
            "transient",
            "try",
            "volatile",
    };

    private static final Character[] WHITESPACE = {
            ' ', '\r', '\n', '\t'
    };

    private static final String[] KEYWORDS = {
      "abstract",
      "assert",
      "boolean",
      "break",
      "byte",
      "case",
      "catch",
      "char",
      "class",
      "const",
      "continue",
      "default",
      "double",
      "do",
      "else",
      "enum",
      "extends",
      "false",
      "finally",
      "final",
      "float",
      "for",
      "goto",
      "if",
      "implements",
      "import",
      "instanceof",
      "interface",
      "int",
      "long",
      "native",
      "new",
      "null",
      "package",
      "private",
      "protected",
      "public",
      "return",
      "short",
      "static",
      "strictfp",
      "super",
      "switch",
      "synchronized",
      "this",
      "throws",
      "throw",
      "transient",
      "true",
      "try",
      "void",
      "volatile",
      "while"
    };
  }
}
