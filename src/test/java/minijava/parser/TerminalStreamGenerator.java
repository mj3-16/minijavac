package minijava.parser;

import static minijava.token.Terminal.*;
import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import minijava.token.Terminal;
import org.jooq.lambda.tuple.Tuple2;

public class TerminalStreamGenerator extends Generator<TerminalStream> {

  /* If we encountered this many stack overflows while
   * generating, we try to take all shortcuts in the grammar that are possible.
   */
  private static final int MAX_OVERFLOWS = 50;
  private final List<Terminal> ret = new ArrayList<>();
  private int sizeHint;
  private int overflows = 0;

  public TerminalStreamGenerator() {
    super(TerminalStream.class);
  }

  @Override
  public TerminalStream generate(SourceOfRandomness random, GenerationStatus status) {
    return new TerminalStream(generateProgram(random));
  }

  public void configure(Size size) {
    sizeHint = size.max();
  }

  // ClassDeclaration*
  public List<Terminal> generateProgram(SourceOfRandomness random) {

    int n = nextArity(random, 10);
    for (int i = 0; i < n; ++i) {
      genClassDeclaration(random);
    }
    ret.add(EOF);

    return ret;
  }

  // class IDENT { ClassMember* }
  private void genClassDeclaration(SourceOfRandomness random) {
    ret.add(CLASS);
    ret.add(IDENT);
    ret.add(LBRACE);

    int numberOfClassMembers = nextArity(random, 10);
    for (int i = 0; i < numberOfClassMembers; ++i) {
      genClassMember(random);
    }

    ret.add(RBRACE);
  }

  // Field | Method | MainMethod
  private void genClassMember(SourceOfRandomness random) {
    selectWithRandomWeight(random, tuple(0.5, this::genField), tuple(0.4, this::genMethod));
    // We can't really generate the identifier String without specifying a concrete Token...
    // Fortunately, this is easy enough to test manually.
    // tuple(0.1, TerminalStreamGenerator::genMainMethod));
  }

  // public Type IDENT ;
  private void genField(SourceOfRandomness random) {
    ret.add(PUBLIC);
    genType(random);
    ret.add(IDENT);
    ret.add(SEMICOLON);
  }

  // public Type IDENT ( Parameters? ) block
  private void genMethod(SourceOfRandomness random) {
    ret.add(PUBLIC);
    genType(random);
    ret.add(IDENT);
    ret.add(LPAREN);

    int n = nextArity(random, 2);
    if (n-- > 0) {
      genParameter(random);
      for (int i = 0; i < n; ++i) {
        ret.add(COMMA);
        genParameter(random);
      }
    }

    ret.add(RPAREN);
    if (random.nextBoolean()) {
      genMethodRest(random);
    }
    genBlock(random);
  }

  // throws IDENT?
  private void genMethodRest(SourceOfRandomness random) {
    ret.add(THROWS);
    ret.add(IDENT);
  }

  // Type IDENT
  private void genParameter(SourceOfRandomness random) {
    genType(random);
    ret.add(IDENT);
  }

  // Type [ ] | BasicType
  private void genType(SourceOfRandomness random) {
    selectWithRandomWeight(
        random,
        tuple(0.8, this::genBasicType),
        // There won't be stack overflow with high probability :>
        tuple(
            0.2,
            r -> {
              genType(r);
              ret.add(LBRACK);
              ret.add(RBRACK);
            }));
  }

  // int | boolean | void | IDENT
  private void genBasicType(SourceOfRandomness random) {
    ret.add(random.choose(Arrays.asList(INT, BOOLEAN, VOID, IDENT)));
  }

  /*
  block
  | EmptyStatement
  | IfStatement
  | ExpressionStatement
  | WhileStatement
  | ReturnStatement
  */
  private void genStatement(SourceOfRandomness random) {
    selectWithRandomWeight(
        random,
        tuple(0.1, this::genEmptyStatement),
        tuple(1.0, this::genExpressionStatement),
        tuple(0.1, this::genBlock),
        tuple(0.1, this::genIfStatement),
        tuple(0.1, this::genWhileStatement),
        tuple(0.1, this::genReturnStatement));
  }

  // { BlockStatement * }
  private void genBlock(SourceOfRandomness random) {
    ret.add(LBRACE);
    int n = nextArity(random, 3);
    for (int i = 0; i < n; ++i) {
      genBlockStatement(random);
    }
    ret.add(RBRACE);
  }

  // Statement | LocalVariableDeclarationStatement
  private void genBlockStatement(SourceOfRandomness random) {
    selectWithRandomWeight(
        random, tuple(0.3, this::genLocalVariableStatement), tuple(0.7, this::genStatement));
  }

  // Type IDENT (= Expression)? ;
  private void genLocalVariableStatement(SourceOfRandomness random) {
    genType(random);
    ret.add(IDENT);
    selectWithRandomWeight(
        random,
        tuple(0.4, r -> {}),
        tuple(
            0.6,
            r -> {
              ret.add(ASSIGN);
              genExpression(r);
            }));
    ret.add(SEMICOLON);
  }

  // ;
  private void genEmptyStatement(SourceOfRandomness random) {
    ret.add(SEMICOLON);
  }

  // while ( Expression ) Statement
  private void genWhileStatement(SourceOfRandomness random) {
    ret.add(WHILE);
    ret.add(LPAREN);
    genExpression(random);
    ret.add(RPAREN);
    genStatement(random);
  }

  // if ( Expression ) Statement (else Statement)?
  private void genIfStatement(SourceOfRandomness random) {
    ret.add(IF);
    ret.add(LPAREN);
    genExpression(random);
    ret.add(RPAREN);
    genStatement(random);

    selectWithRandomWeight(
        random,
        tuple(0.3, r -> {}),
        tuple(
            0.7,
            r -> {
              ret.add(ELSE);
              genStatement(r);
            }));
  }

  // Expression ;
  private void genExpressionStatement(SourceOfRandomness random) {
    genExpression(random);
    ret.add(SEMICOLON);
  }

  // return Expression? ;
  private void genReturnStatement(SourceOfRandomness random) {
    ret.add(RETURN);
    selectWithRandomWeight(
        random,
        tuple(0.3, r -> ret.add(SEMICOLON)),
        tuple(
            0.7,
            r -> {
              genExpression(r);
              ret.add(SEMICOLON);
            }));
  }

  // AssignmentExpression
  private void genExpression(SourceOfRandomness random) {
    while (true) {
      List<Terminal> backup = new ArrayList<>(ret);
      try {
        genAssignmentExpression(random);
        break;
      } catch (StackOverflowError e) {
        ret.clear();
        ret.addAll(backup);
        overflows++; // This is so that we eventually terminate. See followShortcuts().
        // The more overflows we have, the more likely we are to just bubble up further
        double overflowRatio =
            1 - Math.min(1, Math.max(0, overflows / (double) (MAX_OVERFLOWS + 1)));
        if (random.nextDouble() < overflowRatio) {
          // Just try again
          continue;
        }
        // Make more room on the stack
        throw e;
      }
    }
  }

  // LogicalOrExpression (= AssignmentExpression)?
  private void genAssignmentExpression(SourceOfRandomness random) {
    genLogicalOrExpression(random);
    selectWithRandomWeight(
        random,
        tuple(0.7, r -> {}),
        tuple(
            0.3,
            r -> {
              ret.add(ASSIGN);
              genAssignmentExpression(r);
            }));
  }

  /**
   * Practically all of the following gen*Expression productions habe the form
   *
   * <p>(recurse (one of operators))? nextPrecedence
   *
   * <p>So we abstract it.
   */
  private void chooseExpressionLike(
      SourceOfRandomness random,
      Consumer<SourceOfRandomness> nextPrecedence,
      Terminal... operators) {

    List<Tuple2<Double, Consumer<SourceOfRandomness>>> choices = new ArrayList<>();
    // Drops down one level in precedence (e.g. from LogicalOrExpression to LogicalAndExpression).
    // This is the non-recursive case, so should have a rel. high weight.
    choices.add(tuple(2.0, nextPrecedence));

    for (Terminal op : operators) {
      choices.add(
          tuple(
              0.1,
              r -> {
                // In the other cases, we will recurse while also intercalating an operator.
                // Think LogicalOrExpression || LogicalAndExpression
                //           recurse         op     nextPrecedence
                chooseExpressionLike(r, nextPrecedence, operators);
                ret.add(op);
                nextPrecedence.accept(r);
              }));
    }

    selectWithRandomWeight(random, choices);
  }

  // (LogicalOrExpression ||)? LogicalAndExpression
  private void genLogicalOrExpression(SourceOfRandomness random) {
    chooseExpressionLike(random, this::genLogicalAndExpression, OR);
  }

  // (LogicalAndExpression &&)? EqualityExpression
  private void genLogicalAndExpression(SourceOfRandomness random) {
    chooseExpressionLike(random, this::genEqualityExpression, AND);
  }

  // (EqualityExpression (== | !=))? RelationalExpression
  private void genEqualityExpression(SourceOfRandomness random) {
    chooseExpressionLike(random, this::genRelationalExpression, EQL, NEQ);
  }

  // (RelationalExpression (< | <= | > | >=))? AdditiveExpression
  private void genRelationalExpression(SourceOfRandomness random) {
    chooseExpressionLike(random, this::genAdditiveExpression, LSS, LEQ, GTR, GEQ);
  }

  // (AdditiveExpression (+ | -))? MultiplicativeExpression
  private void genAdditiveExpression(SourceOfRandomness random) {
    chooseExpressionLike(random, this::genMultiplicativeExpression, ADD, SUB);
  }

  // (MultiplicativeExpression (* | / | %))? UnaryExpression
  private void genMultiplicativeExpression(SourceOfRandomness random) {
    chooseExpressionLike(random, this::genUnaryExpression, MUL, DIV, MOD);
  }

  // PostfixExpression | (! | -) UnaryExpression
  private void genUnaryExpression(SourceOfRandomness random) {
    selectWithRandomWeight(
        random,
        tuple(0.8, this::genPostfixExpression),
        tuple(
            0.1,
            r -> {
              ret.add(NOT);
              genUnaryExpression(r);
            }),
        tuple(
            0.1,
            r -> {
              ret.add(NOT);
              genUnaryExpression(r);
            }));
  }

  // PrimaryExpression (PostfixOp)*
  private void genPostfixExpression(SourceOfRandomness random) {
    genPrimaryExpression(random);
    int postfixOps = nextArity(random, 3);
    for (int i = 0; i < postfixOps; ++i) {
      genPostfixOp(random);
    }
  }

  /*
  MethodInvocation
  | FieldAccess
  | ArrayAccess
   */
  private void genPostfixOp(SourceOfRandomness random) {
    selectWithRandomWeight(
        random,
        tuple(0.2, this::genFieldAccess),
        tuple(0.6, this::genMethodInvocation),
        tuple(0.2, this::genArrayAccess));
  }

  // . IDENT ( Arguments )
  private void genMethodInvocation(SourceOfRandomness random) {
    ret.add(PERIOD);
    ret.add(IDENT);
    ret.add(LPAREN);
    genArguments(random);
    ret.add(RPAREN);
  }

  // . IDENT
  private void genFieldAccess(SourceOfRandomness random) {
    ret.add(PERIOD);
    ret.add(IDENT);
  }

  // [ Expression ]
  private void genArrayAccess(SourceOfRandomness random) {
    ret.add(LBRACK);
    genExpression(random);
    ret.add(RBRACK);
  }

  // (Expression (, Expression)*)?
  private void genArguments(SourceOfRandomness random) {
    int n = nextArity(random, 3);

    if (n-- > 0) {
      genExpression(random);
      for (int i = 0; i < n; ++i) {
        ret.add(COMMA);
        genExpression(random);
      }
    }
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
  private void genPrimaryExpression(SourceOfRandomness random) {
    selectWithRandomWeight(
        random,
        tuple(0.1, r -> ret.add(NULL)),
        tuple(0.1, r -> ret.add(FALSE)),
        tuple(0.1, r -> ret.add(TRUE)),
        tuple(0.1, r -> ret.add(INTEGER_LITERAL)),
        tuple(0.1, r -> ret.add(IDENT)),
        tuple(
            0.1,
            r -> {
              ret.add(IDENT);
              ret.add(LPAREN);
              genArguments(r);
              ret.add(RPAREN);
            }),
        tuple(0.1, r -> ret.add(THIS)),
        tuple(
            0.1,
            r -> {
              ret.add(LPAREN);
              genExpression(r);
              ret.add(RPAREN);
            }),
        tuple(0.1, this::genNewObjectExpression),
        tuple(0.1, this::genNewArrayExpression));
  }

  // new IDENT ( )
  private void genNewObjectExpression(SourceOfRandomness random) {
    ret.add(NEW);
    ret.add(IDENT);
    ret.add(LPAREN);
    ret.add(RPAREN);
  }

  // new BasicType [ Expression ] ([ ])*
  private void genNewArrayExpression(SourceOfRandomness random) {
    ret.add(NEW);
    genBasicType(random);
    ret.add(LBRACK);
    genExpression(random);
    ret.add(RBRACK);

    int n = nextArity(random, 2);
    for (int i = 0; i < n; ++i) {
      ret.add(LBRACK);
      ret.add(RBRACK);
    }
  }

  // Convenience wrapper
  @SafeVarargs
  private final void selectWithRandomWeight(
      SourceOfRandomness random, Tuple2<Double, Consumer<SourceOfRandomness>>... items) {
    selectWithRandomWeight(random, Arrays.asList(items));
  }

  /**
   * Draws randomly a function from a weighted sequence of items and applies @random@ to it. This
   * could be simpler and more general without the @Function@ bit, but Java's inference sucks balls.
   */
  private void selectWithRandomWeight(
      SourceOfRandomness random, List<Tuple2<Double, Consumer<SourceOfRandomness>>> items) {

    // We silently assume that the first case is always the base-case, e.g. the case breaking the loop.
    // This way we can avoid growing the stack too deep.
    if (followShortcuts()) {
      seq(items).findFirst().get().v2.accept(random);
      return;
    }

    double sumOfWeights = seq(items).map(Tuple2::v1).sum().orElse(0.0);
    double roll = random.nextDouble(0, sumOfWeights);
    for (Tuple2<Double, Consumer<SourceOfRandomness>> item : items) {
      if (roll <= item.v1) {
        item.v2.accept(random);
        return;
      }
      roll -= item.v1;
    }

    // This point will be reached either if rounding errors accumulated (in case we bias towards the last item)
    // or when there are no items to choose from, in which case .get() will throw.
    seq(items).map(Tuple2::v2).reverse().findFirst().get().accept(random);
  }

  /**
   * Arity in the sense, that the number will be used to determine how many children to spawn. If we
   * don't cap this at some point, chances are pretty high we will never terminate.
   */
  private int nextArity(SourceOfRandomness random, int max) {
    double sizeRatio = 1 - Math.min(1, Math.max(0, ret.size() / (double) (sizeHint + 1)));
    double overflowRatio = 1 - Math.min(1, Math.max(0, overflows / (double) (MAX_OVERFLOWS + 1)));
    double ratio = Math.min(sizeRatio, overflowRatio);
    return (int) (random.nextInt(0, max) * ratio);
  }

  private boolean followShortcuts() {
    return ret.size() > sizeHint || overflows >= MAX_OVERFLOWS;
  }
}
