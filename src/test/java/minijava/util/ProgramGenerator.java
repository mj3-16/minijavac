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

public class ProgramGenerator extends Generator<Program> {

  /* If we encountered this many stack overflows while
   * generating, we try to take all shortcuts in the grammar that are possible.
   */
  private static final int MAX_OVERFLOWS = 50;
  private int nodes = 0;
  private int sizeHint;
  private int overflows = 0;

  public ProgramGenerator() {
    super(Program.class);
  }

  @Override
  public Program generate(SourceOfRandomness random, GenerationStatus status) {
    return generateProgram(random);
  }

  public void configure(Size size) {
    sizeHint = size.max();
  }

  // ClassDeclaration*
  private Program generateProgram(SourceOfRandomness random) {
    nodes = 0;
    int n = nextArity(random, 10);
    List<Class> decls = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      decls.add(genClassDeclaration(random));
    }

    nodes++;
    return new Program(decls, SourceRange.FIRST_CHAR);
  }

  // class IDENT { ClassMember* }
  private Class genClassDeclaration(SourceOfRandomness random) {
    int numberOfClassMembers = nextArity(random, 10);
    List<Method> methods = new ArrayList<>();
    List<Field> fields = new ArrayList<>();
    for (int i = 0; i < numberOfClassMembers; ++i) {
      Object member = genClassMember(random);
      if (member instanceof Field) {
        fields.add((Field) member);
      } else {
        methods.add((Method) member);
      }
    }
    nodes++;
    return new Class(genIdent(random), fields, methods, SourceRange.FIRST_CHAR);
  }

  // Field | Method | MainMethod
  private Object genClassMember(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(random, tuple(0.5, this::genField), tuple(0.4, this::genMethod));
  }

  // public Type IDENT ;
  private Field genField(SourceOfRandomness random) {
    nodes++;
    return new Field(genType(random), genIdent(random), SourceRange.FIRST_CHAR);
  }

  // public Type IDENT ( Parameters? ) Block
  private Method genMethod(SourceOfRandomness random) {
    boolean isStatic = random.nextBoolean();
    Type returnType =
        isStatic ? new Type(new Ref<>("void"), 0, SourceRange.FIRST_CHAR) : genType(random);

    int n = nextArity(random, 2);
    List<Method.Parameter> parameters = new ArrayList<>(n);

    if (isStatic) {
      parameters.add(
          new Method.Parameter(
              new Type(new Ref<>("String"), 1, SourceRange.FIRST_CHAR),
              genIdent(random),
              SourceRange.FIRST_CHAR));
    } else {
      for (int i = 0; i < n; ++i) {
        parameters.add(genParameter(random));
      }
    }

    Block body = genBlock(random);

    nodes++;
    return new Method(
        isStatic, returnType, genIdent(random), parameters, body, SourceRange.FIRST_CHAR);
  }

  // Type IDENT
  private Method.Parameter genParameter(SourceOfRandomness random) {
    nodes++;
    return new Method.Parameter(genType(random), genIdent(random), SourceRange.FIRST_CHAR);
  }

  private static String genIdent(SourceOfRandomness random) {
    return TokenGenerator.generateStringForTerminal(IDENT, random);
  }

  // Type [ ] | BasicType
  private Type genType(SourceOfRandomness random) {
    int n = random.nextInt(0, 3);
    String typeName = random.choose(Arrays.asList("void", "int", "boolean", genIdent(random)));
    nodes++;
    return new Type(new Ref<>(typeName), n, SourceRange.FIRST_CHAR);
  }

  /*
  Block
  | EmptyStatement
  | IfStatement
  | ExpressionStatement
  | WhileStatement
  | ReturnStatement
  */
  private Statement genStatement(SourceOfRandomness random) {
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
  private Block genBlock(SourceOfRandomness random) {
    int n = nextArity(random, 3);
    List<BlockStatement> statements = new ArrayList<>(n);
    for (int i = 0; i < n; ++i) {
      statements.add(genBlockStatement(random));
    }
    nodes++;
    return new Block(statements, SourceRange.FIRST_CHAR);
  }

  // Statement | LocalVariableDeclarationStatement
  private BlockStatement genBlockStatement(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(
        random, tuple(0.3, this::genLocalVariableStatement), tuple(0.7, this::genStatement));
  }

  // Type IDENT (= Expression)? ;
  private BlockStatement genLocalVariableStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.Variable(
        genType(random), genIdent(random), genExpression(random), SourceRange.FIRST_CHAR);
  }

  // ;
  private Statement genEmptyStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.Empty(SourceRange.FIRST_CHAR);
  }

  // while ( Expression ) Statement
  private Statement genWhileStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.While(genExpression(random), genStatement(random), SourceRange.FIRST_CHAR);
  }

  // if ( Expression ) Statement (else Statement)?
  private Statement genIfStatement(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(
        random,
        tuple(
            0.3,
            r -> new Statement.If(genExpression(r), genBlock(r), null, SourceRange.FIRST_CHAR)),
        tuple(
            0.7,
            r ->
                new Statement.If(
                    genExpression(r), genBlock(r), genStatement(r), SourceRange.FIRST_CHAR)));
  }

  // Expression ;
  private Statement genExpressionStatement(SourceOfRandomness random) {
    nodes++;
    return new Statement.ExpressionStatement(genExpression(random), SourceRange.FIRST_CHAR);
  }

  // return Expression? ;
  private Statement genReturnStatement(SourceOfRandomness random) {
    nodes++;
    return selectWithRandomWeight(
        random,
        tuple(0.3, r -> new Statement.Return(null, SourceRange.FIRST_CHAR)),
        tuple(0.7, r -> new Statement.Return(genExpression(r), SourceRange.FIRST_CHAR)));
  }

  // AssignmentExpression
  private Expression genExpression(SourceOfRandomness random) {
    while (true) {
      try {
        nodes++;
        return selectWithRandomWeight(
            random,
            tuple(
                0.8, r -> new Expression.Variable(new Ref<>(genIdent(r)), SourceRange.FIRST_CHAR)),
            tuple(0.1, r -> Expression.ReferenceTypeLiteral.this_(SourceRange.FIRST_CHAR)),
            tuple(0.1, r -> Expression.ReferenceTypeLiteral.null_(SourceRange.FIRST_CHAR)),
            tuple(1.0, r -> new Expression.BooleanLiteral(r.nextBoolean(), SourceRange.FIRST_CHAR)),
            tuple(1.0, r -> new Expression.IntegerLiteral(genInt(r), SourceRange.FIRST_CHAR)),
            tuple(
                0.1,
                r ->
                    new Expression.BinaryOperator(
                        r.choose(Expression.BinOp.values()),
                        genExpression(r),
                        genExpression(r),
                        SourceRange.FIRST_CHAR)),
            tuple(
                0.1,
                r ->
                    new Expression.UnaryOperator(
                        r.choose(Expression.UnOp.values()),
                        genExpression(r),
                        SourceRange.FIRST_CHAR)),
            tuple(
                0.1,
                r ->
                    new Expression.MethodCall(
                        genExpression(r),
                        new Ref<>(genIdent(r)),
                        genArguments(r),
                        SourceRange.FIRST_CHAR)),
            tuple(
                0.1,
                r ->
                    new Expression.FieldAccess(
                        genExpression(r), new Ref<>(genIdent(r)), SourceRange.FIRST_CHAR)),
            tuple(
                0.1,
                r ->
                    new Expression.ArrayAccess(
                        genExpression(r), genExpression(r), SourceRange.FIRST_CHAR)),
            tuple(
                0.1, r -> new Expression.NewObject(new Ref<>(genIdent(r)), SourceRange.FIRST_CHAR)),
            tuple(
                0.1,
                r ->
                    new Expression.NewArray(
                        genArrayType(r), genExpression(r), SourceRange.FIRST_CHAR)));
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

  private Type genArrayType(SourceOfRandomness random) {
    Type t = genType(random);
    return new Type(t.basicType, Math.max(t.dimension, 1), SourceRange.FIRST_CHAR);
  }

  private static String genInt(SourceOfRandomness r) {
    return TokenGenerator.generateStringForTerminal(INTEGER_LITERAL, r);
  }

  // (Expression (, Expression)*)?
  private List<Expression> genArguments(SourceOfRandomness random) {
    int n = nextArity(random, 3);
    List<Expression> ret = new ArrayList<>(n);
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
