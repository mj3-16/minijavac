package minijava.parser;

import static minijava.token.Terminal.*;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import minijava.token.Terminal;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class TerminalStreamGenerator {

  // ClassDeclaration*
  public static List<Terminal> generateProgram(SourceOfRandomness random, GenerationStatus status) {
    return Seq.generate(() -> genClassDeclaration(random))
        .limit(status.size())
        .flatMap(cd -> cd) // No flatten?!
        .append(EOF)
        .toList();
  }

  // class IDENT { ClassMember* }
  private static Seq<Terminal> genClassDeclaration(SourceOfRandomness random) {
    int numberOfClassMembers = random.nextInt(0, 10);
    Seq<Terminal> classMembers =
        Seq.generate(() -> genClassMember(random)).limit(numberOfClassMembers).flatMap(cm -> cm);

    return Seq.of(CLASS, IDENT, LCURLY).append(classMembers).append(RCURLY);
  }

  // Field | Method | MainMethod
  private static Seq<Terminal> genClassMember(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.5, TerminalStreamGenerator::genField),
        tuple(0.4, TerminalStreamGenerator::genMethod),
        tuple(0.1, TerminalStreamGenerator::genMainMethod));
  }

  // public Type IDENT ;
  private static Seq<Terminal> genField(SourceOfRandomness random) {
    return Seq.of(PUBLIC).append(genType(random)).append(IDENT, SEMICOLON);
  }

  // public static void IDENT ( String [ ] IDENT ) Block
  private static Seq<Terminal> genMainMethod(SourceOfRandomness random) {
    return Seq.of(PUBLIC, STATIC, VOID, IDENT, LPAREN, STRING, LBRACKET, RBRACKET, IDENT, RPAREN)
        .append(genBlock(random));
  }

  // public Type IDENT ( Parameters? ) Block
  private static Seq<Terminal> genMethod(SourceOfRandomness random) {
    return Seq.of(PUBLIC)
        .append(genType(random))
        .append(IDENT, LPAREN)
        // Inlined Parameters here, because it's easier to generate this way.
        // (Parameter | Parameter , Parameters)?
        .append(
            Seq.generate(() -> genParameter(random))
                .limit(random.nextInt(0, 4))
                .intersperse(Seq.of(COMMA))
                .flatMap(param -> param))
        .append(RPAREN)
        .append(genBlock(random));
  }

  // Type IDENT
  private static Seq<Terminal> genParameter(SourceOfRandomness random) {
    return genType(random).append(IDENT);
  }

  // Type [ ] | BasicType
  private static Seq<Terminal> genType(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.8, TerminalStreamGenerator::genBasicType),
        // There won't be stack overflow with high probability :>
        tuple(0.2, r -> genType(r).append(LBRACKET, RBRACKET)));
  }

  // int | boolean | void | IDENT
  private static Seq<Terminal> genBasicType(SourceOfRandomness random) {
    return Seq.of(random.choose(Arrays.asList(INT, BOOLEAN, VOID, IDENT)));
  }

  /*
  Block
  | EmptyStatement
  | IfStatement
  | ExpressionStatement
  | WhileStatement
  | ReturnStatement
  */
  private static Seq<Terminal> genStatement(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.1, TerminalStreamGenerator::genBlock),
        tuple(0.1, TerminalStreamGenerator::genEmptyStatement),
        tuple(0.2, TerminalStreamGenerator::genIfStatement),
        tuple(0.5, TerminalStreamGenerator::genExpressionStatement),
        tuple(0.2, TerminalStreamGenerator::genWhileStatement),
        tuple(0.1, TerminalStreamGenerator::genReturnStatement));
  }

  // { BlockStatement * }
  private static Seq<Terminal> genBlock(SourceOfRandomness random) {
    return Seq.generate(() -> genBlockStatement(random))
        .limit(random.nextInt(0, 10))
        .flatMap(bs -> bs);
  }

  // Statement | LocalVariableDeclarationStatement
  private static Seq<Terminal> genBlockStatement(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.7, TerminalStreamGenerator::genStatement),
        tuple(0.3, TerminalStreamGenerator::genLocalVariableStatement));
  }

  // Type IDENT (= Expression)? ;
  private static Seq<Terminal> genLocalVariableStatement(SourceOfRandomness random) {
    Seq<Terminal> prefix = genType(random).append(IDENT);
    return selectWithRandomWeight(
        random,
        tuple(0.3, r -> prefix),
        tuple(0.7, r -> prefix.append(EQUAL_SIGN).append(genExpression(r))));
  }

  // ;
  private static Seq<Terminal> genEmptyStatement(SourceOfRandomness random) {
    return Seq.of(SEMICOLON);
  }

  // while ( Expression ) Statement
  private static Seq<Terminal> genWhileStatement(SourceOfRandomness random) {
    return Seq.of(WHILE, LPAREN)
        .append(genExpression(random))
        .append(RPAREN)
        .append(genStatement(random));
  }

  // if ( Expression ) Statement (else Statement)?
  private static Seq<Terminal> genIfStatement(SourceOfRandomness random) {
    Seq<Terminal> prefix =
        Seq.of(IF, LPAREN)
            .append(genExpression(random))
            .append(RPAREN)
            .append(genStatement(random));

    return selectWithRandomWeight(
        random,
        tuple(0.3, r -> prefix),
        tuple(0.7, r -> prefix.append(ELSE).append(genStatement(r))));
  }

  // Expression ;
  private static Seq<Terminal> genExpressionStatement(SourceOfRandomness random) {
    return genExpression(random).append(SEMICOLON);
  }

  // return Expression? ;
  private static Seq<Terminal> genReturnStatement(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.3, r -> Seq.of(RETURN, SEMICOLON)),
        tuple(0.7, r -> Seq.of(RETURN).append(genExpression(r)).append(SEMICOLON)));
  }

  // AssignmentExpression
  private static Seq<Terminal> genExpression(SourceOfRandomness random) {
    return genAssignmentExpression(random);
  }

  // LogicalOrExpression (= AssignmentExpression)?
  private static Seq<Terminal> genAssignmentExpression(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.7, TerminalStreamGenerator::genLogicalOrExpression),
        tuple(
            0.3,
            r -> genLogicalOrExpression(r).append(EQUAL_SIGN).append(genAssignmentExpression(r))));
  }

  /**
   * Practically all of the following gen*Expression productions habe the form
   *
   * <p>(recurse (one of operators))? nextPrecedence
   *
   * <p>So we abstract it.
   */
  private static Seq<Terminal> chooseExpressionLike(
      SourceOfRandomness random,
      Function<SourceOfRandomness, Seq<Terminal>> nextPrecedence,
      Terminal... operators) {
    final Function<SourceOfRandomness, Seq<Terminal>> recurse =
        r -> chooseExpressionLike(r, nextPrecedence, operators);
    return selectWithRandomWeight(
        random,
        // Drops down one level in precedence (e.g. from LogicalOrExpression to LogicalAndExpression).
        // This is the non-recursive case, so should have a rel. high weight.
        Seq.of(tuple(0.8, nextPrecedence))
            .append(
                Seq.of(operators)
                    .map(
                        op ->
                            // In the other cases, we will recurse while also intercalating an operator.
                            // Think LogicalOrExpression || LogicalAndExpression
                            //           recurse         op     nextPrecedence
                            tuple(
                                0.1,
                                r ->
                                    recurse.apply(r).append(op).append(nextPrecedence.apply(r))))));
  }

  // (LogicalOrExpression ||)? LogicalAndExpression
  private static Seq<Terminal> genLogicalOrExpression(SourceOfRandomness random) {
    return chooseExpressionLike(random, TerminalStreamGenerator::genLogicalAndExpression, OR);
  }

  // (LogicalAndExpression &&)? EqualityExpression
  private static Seq<Terminal> genLogicalAndExpression(SourceOfRandomness random) {
    return chooseExpressionLike(random, TerminalStreamGenerator::genEqualityExpression, AND);
  }

  // (EqualityExpression (== | !=))? RelationalExpression
  private static Seq<Terminal> genEqualityExpression(SourceOfRandomness random) {
    return chooseExpressionLike(
        random, TerminalStreamGenerator::genRelationalExpression, EQUALS, UNEQUALS);
  }

  // (RelationalExpression (< | <= | > | >=))? AdditiveExpression
  private static Seq<Terminal> genRelationalExpression(SourceOfRandomness random) {
    return chooseExpressionLike(
        random,
        TerminalStreamGenerator::genAdditiveExpression,
        LOWER,
        LOWER_EQUALS,
        GREATER,
        GREATER_EQUALS);
  }

  // (AdditiveExpression (+ | -))? MultiplicativeExpression
  private static Seq<Terminal> genAdditiveExpression(SourceOfRandomness random) {
    return chooseExpressionLike(
        random, TerminalStreamGenerator::genMultiplicativeExpression, PLUS, MINUS);
  }

  // (MultiplicativeExpression (* | / | %))? UnaryExpression
  private static Seq<Terminal> genMultiplicativeExpression(SourceOfRandomness random) {
    return chooseExpressionLike(
        random, TerminalStreamGenerator::genUnaryExpression, MULTIPLY, DIVIDE, MODULO);
  }

  // PostfixExpression | (! | -) UnaryExpression
  private static Seq<Terminal> genUnaryExpression(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.8, TerminalStreamGenerator::genPostfixExpression),
        tuple(0.1, r -> Seq.of(INVERT).append(genUnaryExpression(r))),
        tuple(0.1, r -> Seq.of(INVERT).append(genUnaryExpression(r))));
  }

  // PrimaryExpression (PostfixOp)*
  private static Seq<Terminal> genPostfixExpression(SourceOfRandomness random) {
    return genPrimaryExpression(random)
        .append(
            Seq.generate(() -> genPostfixOp(random))
                .limit(random.nextInt(0, 3))
                .flatMap(pfe -> pfe));
  }

  /*
  MethodInvocation
  | FieldAccess
  | ArrayAccess
   */
  private static Seq<Terminal> genPostfixOp(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.6, TerminalStreamGenerator::genMethodInvocation),
        tuple(0.2, TerminalStreamGenerator::genFieldAccess),
        tuple(0.2, TerminalStreamGenerator::genArrayAccess));
  }

  // . IDENT ( Arguments )
  private static Seq<Terminal> genMethodInvocation(SourceOfRandomness random) {
    return Seq.of(DOT, IDENT, LPAREN).append(genArguments(random)).append(RPAREN);
  }

  // . IDENT
  private static Seq<Terminal> genFieldAccess(SourceOfRandomness random) {
    return Seq.of(DOT, IDENT);
  }

  // [ Expression ]
  private static Seq<Terminal> genArrayAccess(SourceOfRandomness random) {
    return Seq.of(LBRACKET).append(genExpression(random)).append(RBRACKET);
  }

  // (Expression (, Expression)*)?
  private static Seq<Terminal> genArguments(SourceOfRandomness random) {
    return Seq.generate(() -> genExpression(random))
        .limit(random.nextInt(0, 4))
        .flatMap(arg -> arg);
  }

  /*
  null
  | false
  | true
  | INTEGER_LITERAL
  | IDENT
  | IDENT ( Arguments )
  | this
  | ( Expression )
  | NewObjectExpression
  | NewArrayExpression
   */
  private static Seq<Terminal> genPrimaryExpression(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.1, r -> Seq.of(NULL)),
        tuple(0.1, r -> Seq.of(FALSE)),
        tuple(0.1, r -> Seq.of(TRUE)),
        tuple(0.1, r -> Seq.of(INTEGER_LITERAL)),
        tuple(0.1, r -> Seq.of(IDENT)),
        tuple(0.1, r -> Seq.of(IDENT, LPAREN).append(genArguments(r)).append(RPAREN)),
        tuple(0.1, r -> Seq.of(THIS)),
        tuple(0.1, r -> Seq.of(LPAREN).append(genExpression(r)).append(RPAREN)),
        tuple(0.1, TerminalStreamGenerator::genNewObjectExpression),
        tuple(0.1, TerminalStreamGenerator::genNewArrayExpression));
  }

  // new IDENT ( )
  private static Seq<Terminal> genNewObjectExpression(SourceOfRandomness random) {
    return Seq.of(NEW, IDENT, LPAREN, RPAREN);
  }

  // new BasicType [ Expression ] ([ ])*
  private static Seq<Terminal> genNewArrayExpression(SourceOfRandomness random) {
    return Seq.of(NEW)
        .append(genBasicType(random))
        .append(LBRACKET)
        .append(genExpression(random))
        .append(RBRACKET)
        .append(Seq.of(LBRACKET, RBRACKET).cycle().limit(2 * random.nextInt(0, 3)));
  }

  // Convenience wrapper
  @SafeVarargs
  private static <T> T selectWithRandomWeight(
      SourceOfRandomness random, Tuple2<Double, Function<SourceOfRandomness, T>>... items) {
    return selectWithRandomWeight(random, Seq.of(items));
  }

  /**
   * Draws randomly a function from a weighted sequence of items and applies @random@ to it. This
   * could be simpler and more general without the @Function@ bit, but Java's inference sucks balls.
   */
  private static <T> T selectWithRandomWeight(
      SourceOfRandomness random, Seq<Tuple2<Double, Function<SourceOfRandomness, T>>> items) {
    double sumOfWeights = items.map(Tuple2::v1).sum().orElse(0.0);
    double roll = random.nextDouble(0, sumOfWeights);
    for (Tuple2<Double, Function<SourceOfRandomness, T>> item : items) {
      if (roll <= item.v1) {
        return item.v2.apply(random);
      }
      roll -= item.v1;
    }

    // This point will be reached either if rounding errors accumulated (in case we bias towards the last item)
    // or when there are no items to choose from, in which case .get() will throw.
    return items.map(Tuple2::v2).reverse().findFirst().get().apply(random);
  }
}
