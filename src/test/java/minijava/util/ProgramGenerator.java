package minijava.util;

import static minijava.token.Terminal.*;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.lexer.TokenGenerator;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

public class ProgramGenerator extends Generator<GeneratedProgram> {

  /* If we encountered this many stack overflows while
   * generating, we try to take all shortcuts in the grammar that are possible.
   */
  private static final int MAX_OVERFLOWS = 50;
  private int nodes = 0;
  private int sizeHint;
  private int overflows = 0;

  public ProgramGenerator() {
    super(GeneratedProgram.class);
  }

  @Override
  public GeneratedProgram generate(SourceOfRandomness random, GenerationStatus status) {
    return new GeneratedProgram(generateProgram(random));
  }

  public void configure(Size size) {
    sizeHint = size.max();
  }

  // ClassDeclaration*
  private Program<String> generateProgram(SourceOfRandomness random) {
    nodes = 0;
    int n = nextArity(random, 10);
    List<Class<String>> decls = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      decls.add(genClassDeclaration(random));
    }

    nodes++;
    return new Program<>(decls);
  }

  // class IDENT { ClassMember* }
  private Class<String> genClassDeclaration(SourceOfRandomness random) {
    int numberOfClassMembers = nextArity(random, 10);
    List<Method<String>> methods = new ArrayList<>();
    List<Field<String>> fields = new ArrayList<>();
    for (int i = 0; i < numberOfClassMembers; ++i) {
      Object member = genClassMember(random);
      if (member instanceof Field) {
        fields.add((Field<String>) member);
      } else {
        methods.add((Method<String>) member);
      }
    }
    nodes++;
    return new Class<>(genIdent(random), fields, methods);
  }

  // Field | Method | MainMethod
  private Object genClassMember(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(random, tuple(0.5, this::genField), tuple(0.4, this::genMethod));
  }

  // public Type IDENT ;
  private Field<String> genField(SourceOfRandomness random) {
    nodes++;
    return new Field<>(genType(random), genIdent(random));
  }

  // public Type IDENT ( Parameters? ) Block
  private Method<String> genMethod(SourceOfRandomness random) {
    boolean isStatic = random.nextBoolean();
    Type<String> returnType = isStatic ? new Type<>("void", 0) : genType(random);

    int n = nextArity(random, 2);
    List<Method.Parameter<String>> parameters = new ArrayList<>(n);

    if (isStatic) {
      parameters.add(new Method.Parameter<>(new Type<>("String", 1), genIdent(random)));
    } else {
      for (int i = 0; i < n; ++i) {
        parameters.add(genParameter(random));
      }
    }

    Block<String> body = genBlock(random);

    nodes++;
    return new Method<>(isStatic, returnType, genIdent(random), parameters, body);
  }

  // Type IDENT
  private Method.Parameter<String> genParameter(SourceOfRandomness random) {
    nodes++;
    return new Method.Parameter<>(genType(random), genIdent(random));
  }

  private static String genIdent(SourceOfRandomness random) {
    return TokenGenerator.generateStringForTerminal(IDENT, random);
  }

  // Type [ ] | BasicType
  private Type<String> genType(SourceOfRandomness random) {
    int n = random.nextInt(0, 3);
    String typeName = random.choose(Arrays.asList("void", "int", "boolean", genIdent(random)));
    nodes++;
    return new Type<>(typeName, n);
  }

  /*
  Block
  | EmptyStatement
  | IfStatement
  | ExpressionStatement
  | WhileStatement
  | ReturnStatement
  */
  private Statement<String> genStatement(SourceOfRandomness random) {
    return selectWithRandomWeight(
        random,
        tuple(0.1, this::genEmptyStatement),
        tuple(1.0, this::genExpressionStatement),
        tuple(0.1, this::genBlock),
        tuple(0.1, this::genIfStatement),
        tuple(0.1, this::genWhileStatement),
        tuple(0.1, this::genReturnStatement));
  }

  // { BlockStatement * }
  private Block<String> genBlock(SourceOfRandomness random) {
    int n = nextArity(random, 3);
    List<BlockStatement<String>> statements = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      statements.add(genBlockStatement(random));
    }
    nodes++;
    return new Block<>(statements);
  }

  // Statement | LocalVariableDeclarationStatement
  private BlockStatement<String> genBlockStatement(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(
        random, tuple(0.3, this::genLocalVariableStatement), tuple(0.7, this::genStatement));
  }

  // Type IDENT (= Expression)? ;
  private BlockStatement<String> genLocalVariableStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.Variable<>(genType(random), genIdent(random), genExpression(random));
  }

  // ;
  private Statement<String> genEmptyStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.EmptyStatement<>();
  }

  // while ( Expression ) Statement
  private Statement<String> genWhileStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.While<>(genExpression(random), genStatement(random));
  }

  // if ( Expression ) Statement (else Statement)?
  private Statement<String> genIfStatement(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(
        random,
        tuple(0.3, r -> new Statement.If<>(genExpression(r), genBlock(r), null)),
        tuple(0.7, r -> new Statement.If<>(genExpression(r), genBlock(r), genStatement(r))));
  }

  // Expression ;
  private Statement<String> genExpressionStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.ExpressionStatement<>(genExpression(random));
  }

  // return Expression? ;
  private Statement<String> genReturnStatement(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(
        random,
        tuple(0.3, r -> new Statement.Return<>()),
        tuple(0.7, r -> new Statement.Return<>(genExpression(r))));
  }

  // AssignmentExpression
  private Expression<String> genExpression(SourceOfRandomness random) {
    while (true) {
      try {
        nodes++;
        return selectWithRandomWeight(
            random,
            tuple(0.8, r -> new Expression.VariableExpression<>(genIdent(r))),
            tuple(0.1, r -> new Expression.VariableExpression<>("null")),
            tuple(0.1, r -> new Expression.VariableExpression<>("this")),
            tuple(1.0, r -> new Expression.BooleanLiteralExpression<>(r.nextBoolean())),
            tuple(1.0, r -> new Expression.IntegerLiteralExpression<>(genInt(r))),
            tuple(
                0.1,
                r ->
                    new Expression.BinaryOperatorExpression<>(
                        r.choose(Expression.BinOp.values()), genExpression(r), genExpression(r))),
            tuple(
                0.1,
                r ->
                    new Expression.UnaryOperatorExpression<>(
                        r.choose(Expression.UnOp.values()), genExpression(r))),
            tuple(
                0.1,
                r ->
                    new Expression.MethodCallExpression<>(
                        genExpression(r), genIdent(r), genArguments(r))),
            tuple(0.1, r -> new Expression.FieldAccessExpression<>(genExpression(r), genIdent(r))),
            tuple(
                0.1,
                r -> new Expression.ArrayAccessExpression<>(genExpression(r), genExpression(r))),
            tuple(0.1, r -> new Expression.NewObjectExpression<>(genIdent(r))),
            tuple(
                0.1, r -> new Expression.NewArrayExpression<>(genArrayType(r), genExpression(r))));
      } catch (StackOverflowError e) {
        nodes--;
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

  private Type<String> genArrayType(SourceOfRandomness random) {
    Type<String> t = genType(random);
    return new Type<>(t.typeRef, Math.max(t.dimension, 1));
  }

  private static String genInt(SourceOfRandomness r) {
    return TokenGenerator.generateStringForTerminal(INTEGER_LITERAL, r);
  }

  // (Expression (, Expression)*)?
  private List<Expression<String>> genArguments(SourceOfRandomness random) {
    int n = nextArity(random, 3);
    List<Expression<String>> ret = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      ret.add(genExpression(random));
    }
    return ret;
  }

  // Convenience wrapper
  @SafeVarargs
  private final <T> T selectWithRandomWeight(
      SourceOfRandomness random, Tuple2<Double, Function<SourceOfRandomness, T>>... items) {

    // We silently assume that the first case is always the base-case, e.g. the case breaking the loop.
    // This way we can avoid growing the stack too deep.
    if (followShortcuts()) {
      return Seq.of(items).findFirst().get().v2.apply(random);
    }

    double sumOfWeights = Seq.of(items).map(Tuple2::v1).sum().orElse(0.0);
    double roll = random.nextDouble(0, sumOfWeights);
    for (Tuple2<Double, Function<SourceOfRandomness, T>> item : items) {
      if (roll <= item.v1) {
        return item.v2.apply(random);
      }
      roll -= item.v1;
    }

    // This point will be reached either if rounding errors accumulated (in case we bias towards the last item)
    // or when there are no items to choose from, in which case .get() will throw.
    return Seq.of(items).map(Tuple2::v2).reverse().findFirst().get().apply(random);
  }

  /**
   * Arity in the sense, that the number will be used to determine how many children to spawn. If we
   * don't cap this at some point, chances are pretty high we will never terminate.
   */
  private int nextArity(SourceOfRandomness random, int max) {
    double sizeRatio = 1 - Math.min(1, Math.max(0, nodes / (double) (sizeHint + 1)));
    double overflowRatio = 1 - Math.min(1, Math.max(0, overflows / (double) (MAX_OVERFLOWS + 1)));
    double ratio = Math.min(sizeRatio, overflowRatio);
    return (int) (random.nextInt(0, max) * ratio);
  }

  private boolean followShortcuts() {
    return nodes > sizeHint || overflows >= MAX_OVERFLOWS;
  }
}
