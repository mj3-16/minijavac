package minijava;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import com.google.common.primitives.Bytes;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jooq.lambda.Seq;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class LexerProperties {

  private static List<Byte> asByteStream(String s) {
    return Bytes.asList(s.getBytes());
  }

  @Property
  public void prettyPrintAndLexIsIdentity(
      List<@From(ActualTokenGenerator.class) ActualToken> tokens) {
    System.out.println(Iterables.toString(tokens));
    Seq<Token> notWanted = Seq.of(Token.EOF, Token.COMMENT, Token.WS, Token.LBRK);
    List<ActualToken> printable =
        seq(tokens)
            .filter(t -> !notWanted.contains(t.tok))
            .append(new ActualToken(Token.EOF, Collections.emptyList()))
            .toList();

    Iterable<Token> lexed = new Lexer(prettyPrint(printable));
    System.out.println(Iterables.toString(lexed));
    System.out.println(Iterables.toString(printable));
    Assert.assertArrayEquals(seq(printable).map(t -> t.tok).toArray(), seq(lexed).toArray());
  }

  private Iterable<Byte> prettyPrint(Iterable<ActualToken> tokens) {
    Token last = Token.EOF;
    List<Byte> bytes = new ArrayList<>(4096);
    for (ActualToken t : tokens) {
      if (t.text.size() > 0) {
        if (t.tok.isAlphanumeric() && last.isAlphanumeric()
            || t.tok.isOperator() && last.isOperator()) {
          bytes.add((byte) ' ');
        }
        bytes.addAll(t.text);
        last = t.tok;
      }
    }
    return bytes;
  }

  public static class ActualToken {
    public final Token tok;
    public final List<Byte> text;

    public ActualToken(Token tok, List<Byte> text) {
      this.tok = tok;
      this.text = text;
    }

    @Override
    public String toString() {
      return tok.toString() + "(" + new String(Bytes.toArray(text)) + ")";
    }
  }

  public static class ActualTokenGenerator extends Generator<ActualToken> {
    public ActualTokenGenerator() {
      super(ActualToken.class);
    }

    @Override
    public ActualToken generate(SourceOfRandomness random, GenerationStatus status) {
      Token[] toks = Token.values();
      Token tok = random.choose(toks);

      return new ActualToken(tok, genActualToken(tok, random));
    }

    private static List<Byte> genActualToken(Token tok, SourceOfRandomness random) {
      switch (tok) {
        case EOF:
          return Collections.emptyList();
        case SYSTEM_OUT_PRINTLN:
          return asByteStream("System.out.println");
        case BOOLEAN:
          return asByteStream("boolean");
        case INT:
          return asByteStream("int");
        case CLASS:
          return asByteStream("class");
        case NEW:
          return asByteStream("new");
        case RETURN:
          return asByteStream("return");
        case THIS:
          return asByteStream("this");
        case IF:
          return asByteStream("if");
        case WHILE:
          return asByteStream("while");
        case ELSE:
          return asByteStream("else");
        case TRUE:
          return asByteStream("true");
        case FALSE:
          return asByteStream("false");
        case PUBLIC:
          return asByteStream("public");
        case STATIC:
          return asByteStream("static");
        case VOID:
          return asByteStream("void");
        case NULL:
          return asByteStream("null");
        case STRING:
          return asByteStream("string");
        case RESERVED_KEYWORDS:
          return asByteStream(random.choose(KEYWORDS));
        case MULTIPLY_EQUALS:
          return asByteStream("*=");
        case INCREMENT:
          return asByteStream("++");
        case PLUS_EQUALS:
          return asByteStream("+=");
        case MINUS_EQUALS:
          return asByteStream("-=");
        case DECREMENT:
          return asByteStream("--");
        case DIVIDE_EQUALS:
          return asByteStream("/=");
        case LOWER_LOWER_EQUALS:
          return asByteStream("<<=");
        case LOWER_LOWER:
          return asByteStream("<<");
        case LOWER_EQUALS:
          return asByteStream("<=");
        case GREATER_EQUALS:
          return asByteStream(">=");
        case GREATER_GREATER_EQUALS:
          return asByteStream(">>=");
        case GREATER_GREATER_GREATER_EQUALS:
          return asByteStream(">>>=");
        case GREATER_GREATER_GREATER:
          return asByteStream(">>>");
        case GREATER_GREATER:
          return asByteStream(">>");
        case MODULO_EQUALS:
          return asByteStream("%=");
        case MODULO:
          return asByteStream("%");
        case AND_EQUALS:
          return asByteStream("&=");
        case BIT_WISE_END:
          return asByteStream("&");
        case LBRACKET:
          return asByteStream("[");
        case RBRACKET:
          return asByteStream("]");
        case XOR:
          return asByteStream("^");
        case BIT_WISE_NOT:
          return asByteStream("~");
        case BIT_WISE_OR:
          return asByteStream("|");
        case PLUS:
          return asByteStream("+");
        case MINUS:
          return asByteStream("-");
        case DIVIDE:
          return asByteStream("/");
        case MULTIPLY:
          return asByteStream("*");
        case EQUAL_SIGN:
          return asByteStream("=");
        case EQUALS:
          return asByteStream("==");
        case UNEQUALS:
          return asByteStream("!=");
        case INVERT:
          return asByteStream("!");
        case LOWER:
          return asByteStream("<");
        case GREATER:
          return asByteStream(">");
        case AND:
          return asByteStream("&");
        case OR:
          return asByteStream("|");
        case LPAREN:
          return asByteStream("(");
        case RPAREN:
          return asByteStream(")");
        case QUESTION_MARK:
          return asByteStream("?");
        case SEMICOLON:
          return asByteStream(";");
        case INTEGER_LITERAL:
          return asByteStream(random.nextBigInteger(random.nextInt(1, 32)).toString());
        case IDENT:
          return Seq.of(random.choose(IDENT_FIRST_CHAR))
              .concat(Seq.generate(() -> random.choose(IDENT_FOLLOWING_CHARS)))
              .limit(random.nextInt(1, 30))
              .toList();
        case LCURLY:
          return asByteStream("{");
        case RCURLY:
          return asByteStream("}");
        case COLON:
          return asByteStream(":");
        case COMMA:
          return asByteStream(",");
        case DOT:
          return asByteStream(".");
        default:
          return asByteStream("");
      }
    }

    private static final Byte[] IDENT_FIRST_CHAR =
        IntStream.concat(
                IntStream.of('_'),
                IntStream.concat(IntStream.rangeClosed('A', 'Z'), IntStream.rangeClosed('a', 'z')))
            .mapToObj(c -> (byte) c)
            .toArray(Byte[]::new);

    private static final Byte[] IDENT_FOLLOWING_CHARS =
        IntStream.concat(
                IntStream.of('_'),
                IntStream.concat(
                    IntStream.rangeClosed('A', 'Z'),
                    IntStream.concat(
                        IntStream.rangeClosed('a', 'z'), IntStream.rangeClosed('0', '9'))))
            .mapToObj(c -> (byte) c)
            .toArray(Byte[]::new);

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

enum Token {
  EOF,
  COMMENT,
  WS,
  LBRK,
  SYSTEM_OUT_PRINTLN,
  BOOLEAN,
  INT,
  CLASS,
  NEW,
  RETURN,
  THIS,
  IF,
  WHILE,
  ELSE,
  TRUE,
  FALSE,
  PUBLIC,
  STATIC,
  VOID,
  NULL,
  STRING,
  RESERVED_KEYWORDS,
  MULTIPLY_EQUALS,
  INCREMENT,
  PLUS_EQUALS,
  MINUS_EQUALS,
  DECREMENT,
  DIVIDE_EQUALS,
  LOWER_LOWER_EQUALS,
  LOWER_LOWER,
  LOWER_EQUALS,
  GREATER_EQUALS,
  GREATER_GREATER_EQUALS,
  GREATER_GREATER_GREATER_EQUALS,
  GREATER_GREATER_GREATER,
  GREATER_GREATER,
  MODULO_EQUALS,
  MODULO,
  AND_EQUALS,
  BIT_WISE_END,
  LBRACKET,
  RBRACKET,
  XOR,
  BIT_WISE_NOT,
  BIT_WISE_OR,
  PLUS,
  MINUS,
  DIVIDE,
  MULTIPLY,
  EQUAL_SIGN,
  EQUALS,
  UNEQUALS,
  INVERT,
  LOWER,
  GREATER,
  AND,
  OR,
  LPAREN,
  RPAREN,
  QUESTION_MARK,
  SEMICOLON,
  INTEGER_LITERAL,
  IDENT,
  LCURLY,
  RCURLY,
  COLON,
  COMMA,
  DOT;

  public boolean isAlphanumeric() {
    return ordinal() >= SYSTEM_OUT_PRINTLN.ordinal() && ordinal() <= RESERVED_KEYWORDS.ordinal();
  }

  public boolean isOperator() {
    return ordinal() >= MULTIPLY_EQUALS.ordinal() && ordinal() <= DOT.ordinal();
  }
}

class Lexer implements Iterable<Token> {
  private final Iterable<Byte> input;

  public Lexer(Iterable<Byte> input) {
    this.input = input;
  }

  @Override
  public Iterator<Token> iterator() {
    return Stream.of(Token.EQUALS, Token.EOF).iterator();
  }
}
