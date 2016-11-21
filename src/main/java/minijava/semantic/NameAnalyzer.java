package minijava.semantic;

import static org.jooq.lambda.tuple.Tuple.tuple;

import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Expression.BooleanLiteral;
import minijava.ast.Expression.IntegerLiteral;
import minijava.ast.Expression.NewArray;
import minijava.ast.Expression.NewObject;
import minijava.ast.Method.Parameter;
import minijava.ast.Statement.ExpressionStatement;
import minijava.util.SourceRange;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple2;

/**
 * Performs name analysis for a Program.
 *
 * <p>It does so by first collecting all class definitions (including field and method names) in a
 * first pass, then will resolve names and perform type checks in a tree traversing manner.
 *
 * <p>Expressions aren't only resolved, but also their types are inferred. This is why we do it in
 * lock-step by returning a Pair.
 */
public class NameAnalyzer
    implements Program.Visitor<Program>,
        Class.Visitor<Class>,
        Field.Visitor<Field>,
        Type.Visitor<Type>,
        Method.Visitor<Method>,
        BlockStatement.Visitor<BlockStatement>,
        Expression.Visitor<Tuple2<Expression, Type>> {
  /**
   * A dummy THIS_EXPR to be used when a Expression.Variable is determined to be really a
   * Expression.FieldAccess to this. Example:
   *
   * <p>class A { int x; public void f() { x = 4; } }
   *
   * <p>The left hand side of the assignment to x will be parsed as a Expression.Variable, but will
   * fall back to FieldAccess in name analysis, when it's clear there is no such local variable.
   */
  private static final Expression THIS_EXPR =
      Expression.ReferenceTypeLiteral.this_(SourceRange.FIRST_CHAR);
  /**
   * Tracks all types in the Program. This will be computed in a prior pass to be able to resolve
   * MethodCalls and FieldAccesses.
   */
  private SymbolTable<BasicType> types = new SymbolTable<>();
  /** The class whose body is under analysis. */
  private Class currentClass;
  /** Tracks all fields in the current class body. */
  private SymbolTable<Field> fields = new SymbolTable<>();
  /** The method whose body is under analysis. */
  private Method currentMethod;
  /** Tracks all local variables in the current method body. */
  private SymbolTable<Definition> locals = new SymbolTable<>();
  /**
   * Tracks the possibly defined main method for the Program under analysis. There must be exactly
   * one static main method.
   */
  private Method mainMethod;
  /**
   * This flag tracks if we have encountered a return statement on all control flow paths leading to
   * the current statement. This is only modified in if and while statements. The information is
   * used when having visited the body of a method, when this flag has to be true when the return
   * type was is non-void.
   */
  private boolean hasReturned;

  /**
   * We need to 1. collect all class declarations (including fields and methods) in a first pass
   * with TypeCollector 2. Actually visit each class body to resolve referenced names. 3. Check that
   * we found a main method.
   */
  @Override
  public Program visitProgram(Program that) {
    mainMethod = null;
    // collect all types first (throws if duplicates exist)
    this.types = that.acceptVisitor(new TypeCollector());
    List<Class> refClasses = new ArrayList<>(that.declarations.size());
    for (Class c : that.declarations) {
      Class refClass = c.acceptVisitor(this);
      refClasses.add(refClass);
    }
    if (mainMethod == null) {
      throw new SemanticError(that.range(), "No main method defined");
    }
    return new Program(refClasses, that.range());
  }

  /**
   * 1. Collect and resolve field references, checking for duplicates. 2. Collect and resolve method
   * references, including bodies, checking for duplicates.
   */
  @Override
  public Class visitClassDeclaration(Class that) {
    currentClass = that;
    // fields in current class
    fields = new SymbolTable<>();
    fields.enterScope();
    List<Field> newFields = new ArrayList<>(that.fields.size());
    for (Field f : that.fields) {
      if (fields.inCurrentScope(f.name())) {
        throw new SemanticError(
            f.range(), "Field '" + f.name() + "' is already defined in this scope");
      }
      fields.insert(f.name(), f);
      Field field = f.acceptVisitor(this);
      newFields.add(field);
    }

    // We only need the symbol table for methods for checking for duplicates.
    // At call sites, the called method is resolved by looking it up in the
    // body of the class type left to the dot.
    SymbolTable<Method> methods = new SymbolTable<>();
    methods.enterScope();
    List<Method> newMethods = new ArrayList<>(that.methods.size());
    for (Method m : that.methods) {
      if (methods.inCurrentScope(m.name())) {
        throw new SemanticError(
            m.range(), "Method '" + m.name() + "' is already defined in this scope");
      }
      methods.insert(m.name(), m);
      currentMethod = m;
      Method method = m.acceptVisitor(this);
      newMethods.add(method);
    }

    return new Class(that.name(), newFields, newMethods, that.range());
  }

  /** Fields need to have their type resolved. Also that type musn't be void. */
  @Override
  public Field visitField(Field that) {
    Type type = that.type.acceptVisitor(this);
    checkElementTypeIsNotVoid(type, type.range());
    return new Field(type, that.name(), that.range(), new Ref<>(currentClass));
  }

  /**
   * Types are always arrays of a certain element type BasicType, which must be present in the
   * pre-collected types SymbolTable.
   */
  @Override
  public Type visitType(Type that) {
    String typeName = that.basicType.name();
    Optional<BasicType> optDef = types.lookup(typeName);
    if (!optDef.isPresent()) {
      throw new SemanticError(that.range(), "Type '" + typeName + "' is not defined");
    }
    return new Type(new Ref<>(optDef.get()), that.dimension, that.range());
  }

  /**
   * Methods are quite involved.
   *
   * <p>1. Resolve the return type. 2. Check types and transform parameters. 2a. Handle the main
   * method. Replace its argument's type with void to make it unusable. The rest is just sanity
   * checks. 2b. Otherwise the method isn't static and we go through the parameter list and resolve
   * types. 3. Add parameters to the locals SymbolTable, checking for duplicate names 4. Analyze the
   * body (remember to set hasReturned to false) 5. Check if hasReturned is true. If not, the return
   * type must be void. Otherwise we didn't return on all paths.
   */
  @Override
  public Method visitMethod(Method that) {
    // 1.
    Type returnType = that.returnType.acceptVisitor(this);

    // 2. Check types and transform parameters
    List<Parameter> newParams = new ArrayList<>(that.parameters.size());
    // main gets special treatment. We replace its parameter type by void, to make it unusable.
    if (that.isStatic) {
      // We begin with some sanity checks that the parser should already have verified.
      if (!that.name().equals("main")) {
        throw new SemanticError(that.range(), "Static methods must be named main.");
      }
      if (that.parameters.size() != 1) {
        throw new SemanticError(that.range(), "The main method must have exactly one parameter.");
      }

      Parameter p = that.parameters.get(0);
      Type type = p.type;
      if (!type.basicType.name().equals("String") || type.dimension != 1) {
        throw new SemanticError(
            that.range(), "The main method's parameter must have type String[].");
      }
      checkType(Type.VOID, returnType, returnType.range());

      // Here begins the actual interesting part where things can go wrong.
      if (mainMethod != null) {
        throw new SemanticError(
            that.range(), "There is already a main method defined at " + mainMethod.range());
      }
      mainMethod = that;
      // Here we save the String[] parameter as type void instead.
      // This is a hack so that we can't access it in the body, but it leads to confusing error messages.
      // An alternative would be to define another special type like void for better error messages.
      newParams.add(new Parameter(Type.VOID, p.name, p.range()));
    } else { // !isStatic
      for (Parameter p : that.parameters) {
        Type type = p.type.acceptVisitor(this);
        checkElementTypeIsNotVoid(type, p.range());
        newParams.add(new Parameter(type, p.name(), p.range()));
      }
    }

    // We collect local variables into a fresh symboltable. We need these later on,
    // when we try to resolve Variables via FieldAccess on this. in method bodies.
    locals = new SymbolTable<>();
    locals.enterScope();

    // check for parameters with same name
    for (Parameter p : newParams) {
      if (locals.lookup(p.name()).isPresent()) {
        throw new SemanticError(
            p.range(), "There is already a parameter defined with name '" + p.name() + "'");
      }
      locals.insert(p.name(), p);
    }

    hasReturned = false;
    Block block = (Block) that.body.acceptVisitor(this);
    locals.leaveScope();
    // Check if we returned on each possible path and if not, that the return type was void.
    if (!hasReturned) {
      // Check if we implicitly returned
      checkType(Type.VOID, returnType, returnType.range());
    }
    return new Method(
        that.isStatic,
        returnType,
        that.name(),
        newParams,
        block,
        that.range(),
        new Ref<>(currentClass));
  }

  /** Straightforward. Remember to open a new scope for the block. */
  @Override
  public Block visitBlock(Block that) {
    locals.enterScope();
    List<BlockStatement> newStatements = new ArrayList<>(that.statements.size());
    for (BlockStatement s : that.statements) {
      // This also picks up local var decls into @locals@ on the go
      newStatements.add(s.acceptVisitor(this));
    }
    locals.leaveScope();
    return new Block(newStatements, that.range());
  }

  @Override
  public Statement visitEmpty(Statement.Empty that) {
    return new Statement.Empty(that.range());
  }

  /**
   * 1. check that the condition expression is well-typed and returns a boolean 2. visit then 3.
   * visit else
   *
   * <p>Also remember to open and close Scopes. The hasReturned flag also deserves some special
   * mention.
   */
  @Override
  public Statement visitIf(Statement.If that) {
    Tuple2<Expression, Type> cond = that.condition.acceptVisitor(this);
    Expression newCondition = cond.v1;
    checkType(Type.BOOLEAN, cond.v2, cond.v1.range());

    // Save the old returned flag. If this was true, hasReturned will be true afterwards,
    // no matter what the results of the two branches are.
    boolean oldHasReturned = hasReturned;
    hasReturned = false; // This is actually not necessary, but let's be explicit

    locals.enterScope();
    // then might be a block and therefore enters and leaves another subscope, but that's ok
    Statement newThen = (Statement) that.then.acceptVisitor(this);
    locals.leaveScope();

    // Save the result from then
    boolean thenHasReturned = hasReturned;
    hasReturned = false;

    Statement newElse = null;
    if (that.else_.isPresent()) {
      locals.enterScope();
      newElse = (Statement) that.else_.get().acceptVisitor(this);
      locals.leaveScope();
    } // else hasReturned would also be false (we could always fall through the if statement)

    boolean elseHasReturned = hasReturned;

    // Either we returned before this is even executed or we returned in both branches. Otherwise we didn't return.
    hasReturned = oldHasReturned || thenHasReturned && elseHasReturned;
    return new Statement.If(newCondition, newThen, newElse, that.range());
  }

  @Override
  public Statement visitExpressionStatement(ExpressionStatement that) {
    // We don't care for the expressions type, as long as there is one
    // ... although we probably want to safe types in (Typed-)Ref later on
    Expression expr = that.expression.acceptVisitor(this).v1;
    return new ExpressionStatement(expr, that.range());
  }

  /** Analogous to If. */
  @Override
  public Statement visitWhile(Statement.While that) {
    Tuple2<Expression, Type> cond = that.condition.acceptVisitor(this);
    checkType(Type.BOOLEAN, cond.v2, cond.v1.range());

    // In contrast to if, where we actually have two branches of control flow, there's no elaborate
    // hasReturned handling necessary here.
    // Suppose hasReturned was true, then it will stay true throughout the rest of the method.
    // Suppose hasReturned was false, then we won't set it to true, even if the body says so.
    // We can't always predict the value of the condition after all.
    //
    // So: just save the flag before the body and restore it after analyzing the body.

    boolean oldHasReturned = hasReturned;

    locals.enterScope();
    // if body is a block it might enter another subscope (see visitBlock) but that doesn't really matter)
    Statement body = (Statement) that.body.acceptVisitor(this);
    locals.leaveScope();

    hasReturned = oldHasReturned;

    return new Statement.While(cond.v1, body, that.range());
  }

  /**
   * If the expression to return is present, we visit it and make sure that the type matches the
   * current methods return type. Otherwise, the current methods return type must be void.
   */
  @Override
  public Statement visitReturn(Statement.Return that) {
    Type returnType = currentMethod.returnType.acceptVisitor(this);
    if (that.expression.isPresent()) {
      if (!currentMethod.equals(mainMethod)) {
        checkElementTypeIsNotVoid(returnType, returnType.range());
        Tuple2<Expression, Type> expr = that.expression.get().acceptVisitor(this);
        checkType(returnType, expr.v2, expr.v1.range());
        hasReturned = true;
        return new Statement.Return(expr.v1, that.range());
      } else {
        throw new SemanticError(
            that.expression.get().range(), "Returning a value is not valid in main method");
      }
    } else {
      checkType(Type.VOID, returnType, returnType.range());
      hasReturned = true;
      return new Statement.Return(null, that.range());
    }
  }

  /**
   * This is reflexive in both arguments, so swapping them should not change the outcome of this
   * function.
   *
   * <p>1. When the types are equal, we're OK. 2. If not and one of the basic types was void, we
   * have an error (recall that when both are void, the dimensions mismatch). 3. Encode that type
   * ANY_REF is compatible with any reference type, e.g. Class. 4. otherwise there's a type
   * mismatch.
   */
  private void checkType(Type expected, Type actual, SourceRange range) {
    SemanticError e =
        new SemanticError(range, "Expected type '" + expected + "', but got type '" + actual + "'");

    // 1.
    if (expected.dimension == actual.dimension
        && expected.basicType.name().equals(actual.basicType.name())) {
      return;
    }

    // 2. If any of the element types is now void, we should throw.
    if (expected.basicType.name().equals("void") || actual.basicType.name().equals("void")) throw e;

    // 3. The only way this could ever work out is that either actual or expected is of type Any (type of null)
    // and the other is a reference type (every remaining type except non-array builtins).
    // Remember that actual != expected and that either dimensions or the basicType mismatch
    if (expected == Type.ANY_REF && (actual.dimension > 0 || actual.basicType.def instanceof Class))
      return;
    if (actual == Type.ANY_REF
        && (expected.dimension > 0 || expected.basicType.def instanceof Class)) return;

    // 4.
    throw e;
  }

  private void checkElementTypeIsNotVoid(Type actual, SourceRange range) {
    if (actual.basicType.name().equals("void")) {
      throw new SemanticError(range, "Type void is not valid here");
    }
  }

  private void checkIsArrayType(Type actual, SourceRange range) {
    if (actual.dimension == 0) {
      throw new SemanticError(range, "Expected an array type");
    }
  }

  /**
   * The only notable thing here is that if the RHS is present, its type must match the type of the
   * variable declaration.
   */
  public BlockStatement visitVariable(BlockStatement.Variable that) {
    Type variableType = that.type.acceptVisitor(this);
    checkElementTypeIsNotVoid(variableType, variableType.range());
    if (locals.lookup(that.name()).isPresent()) {
      throw new SemanticError(that.range(), "Cannot redefine '" + that.name() + "'");
    }

    Expression rhs = null;

    if (that.rhs.isPresent()) {
      Tuple2<Expression, Type> ret = that.rhs.get().acceptVisitor(this);
      checkType(variableType, ret.v2, ret.v1.range());
      rhs = ret.v1;
    }

    BlockStatement.Variable var =
        new BlockStatement.Variable(variableType, that.name(), rhs, that.range());
    locals.insert(that.name(), var);
    return var;
  }

  /**
   * This is mostly interesting from a type-checking perspective. Look into the respective switch
   * cases.
   */
  @Override
  public Tuple2<Expression, Type> visitBinaryOperator(Expression.BinaryOperator that) {
    Tuple2<Expression, Type> left = that.left.acceptVisitor(this);
    Tuple2<Expression, Type> right = that.right.acceptVisitor(this);

    Type resultType = null; // The result of that switch statement
    switch (that.op) {
      case ASSIGN:
        // make sure that we can actually assign something to left.v1, e.g.
        // it should be a fieldaccess or variableexpression.
        if (!(left.v1 instanceof Expression.FieldAccess)
            && !(left.v1 instanceof Expression.Variable)
            && !(left.v1 instanceof Expression.ArrayAccess)) {
          throw new SemanticError(left.v1.range(), "Expression is not assignable");
        }
        // Also left.v2 must match right.v2
        checkType(
            left.v2,
            right.v2,
            right.v1.range()); // This would be broken for type Any, but null can't be assigned to
        // The result type of the assignment expression is just left.v2
        resultType = left.v2;
        break;
      case PLUS:
      case MINUS:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
        // int -> int -> int
        // so, check that left.v1 and left.v2 is int
        checkType(Type.INT, left.v2, left.v1.range());
        checkType(Type.INT, right.v2, right.v1.range());
        // Then we can just reuse left's type
        resultType = left.v2;
        break;
      case OR:
      case AND:
        // bool -> bool -> bool
        checkType(Type.BOOLEAN, left.v2, left.v1.range());
        checkType(Type.BOOLEAN, right.v2, right.v1.range());
        resultType = left.v2;
        break;
      case EQ:
      case NEQ:
        // T -> T -> bool
        // The Ts have to match
        checkType(left.v2, right.v2, right.v1.range());
        resultType = Type.BOOLEAN;
        break;
      case LT:
      case LEQ:
      case GT:
      case GEQ:
        // int -> int -> bool
        checkType(Type.INT, left.v2, left.v1.range());
        checkType(Type.INT, right.v2, right.v1.range());
        resultType = Type.BOOLEAN;
        break;
    }

    return tuple(
        new Expression.BinaryOperator(that.op, left.v1, right.v1, that.range()), resultType);
  }

  @Override
  public Tuple2<Expression, Type> visitUnaryOperator(Expression.UnaryOperator that) {

    Type expected = null;
    switch (that.op) {
      case NOT:
        // bool -> bool
        expected = Type.BOOLEAN;
        break;
      case NEGATE:
        // int -> int
        if (that.expression instanceof IntegerLiteral) {
          IntegerLiteral lit =
              (IntegerLiteral) that.expression; // that cast is safe, see visitIntegerLiteral
          return handleNegativeIntegerLiterals(that, lit);
        }
        expected = Type.INT;
        break;
    }

    Tuple2<Expression, Type> expr = that.expression.acceptVisitor(this);
    checkType(expected, expr.v2, expr.v1.range());

    return tuple(new Expression.UnaryOperator(that.op, expr.v1, that.range()), expr.v2);
  }

  @NotNull
  private Tuple2<Expression, Type> handleNegativeIntegerLiterals(
      Expression.UnaryOperator that, IntegerLiteral lit) {
    // handle the case of negative literals
    // This is non-compositional, because 0x80000000 is a negative number,
    // but its absolute value cannot be represented in 32 bits, thus is
    // not a valid integer literal.
    if (Ints.tryParse("-" + lit.literal) == null) {
      // insert range
      throw new SemanticError(
          lit.range(), "The literal '-" + lit.literal + "' is not a valid 32-bit number");
    }

    // Also, for the case that we got -(2147483648), which will be parsed
    // as the exact same AST, we have to reject, because this is checked
    // differently by Java.
    // Since we don't save parentheses in the AST (rightly so), we differentiate
    // by SourceRange :ugly_face:
    int minusTokenNumber = that.range().begin.tokenNumber;
    int litTokenNumber = lit.range().begin.tokenNumber;
    if (litTokenNumber > minusTokenNumber + 1 && Ints.tryParse(lit.literal) == null) {
      // MINUS INT(2147483648)
      //  ^ minusTokenNumber
      //       ^ litTokenNumber = minusTokenNumber + 1
      //
      // vs.
      //
      // MINUS LPAREN INT(2147483648) RPAREN
      //   ^ minusTokenNumber
      //              ^ litTokenNumber > minusTokenNumber + 1

      throw new SemanticError(
          lit.range(), "The literal '" + lit.literal + "' is not a valid 32-bit number");
    }
    // Otherwise just return what we know
    return tuple(new Expression.UnaryOperator(that.op, lit, that.range()), Type.INT);
  }

  @Override
  public Tuple2<Expression, Type> visitMethodCall(Expression.MethodCall that) {

    // This will be null if this wasn't a call to System.out.println
    Tuple2<Expression, Type> self = systemOutPrintlnHackForSelf(that);
    if (self == null) {
      // That was not a call matching System.out.println(). So we procede regularly
      self = that.self.acceptVisitor(this);
    }

    Optional<Class> definingClass = isClass(self.v2);

    if (!definingClass.isPresent()) {
      throw new SemanticError(that.range(), "Only classes have methods");
    }

    // This will find the method in the class body of the self object
    Optional<Method> methodOpt =
        definingClass
            .get()
            .methods
            .stream()
            .filter(m -> m.name().equals(that.method.name()))
            .findFirst();

    if (!methodOpt.isPresent()) {
      throw new SemanticError(
          that.range(),
          "Class '" + definingClass.get().name() + "' has no method '" + that.method.name() + "'");
    }

    Method m = methodOpt.get();
    m.definingClass = new Ref<>(definingClass.get());

    if (m.isStatic) {
      throw new SemanticError(that.range(), "Static methods cannot be called.");
    }

    // Now that we have the method, we have access to its type.
    // Check that argument types match declared parameter types!
    if (m.parameters.size() != that.arguments.size()) {
      throw new SemanticError(
          that.range(),
          "Number of declared parameters and actual number of arguments mismatch. Expected "
              + m.parameters.size()
              + " but got "
              + that.arguments.size());
    }

    // We have to resolve argument expressions and match their type against the called
    // method's parameter types.
    List<Expression> resolvedArguments = new ArrayList<>(that.arguments.size());
    for (int i = 0; i < m.parameters.size(); ++i) {
      Type paramType = m.parameters.get(i).type.acceptVisitor(this);
      Tuple2<Expression, Type> arg = that.arguments.get(i).acceptVisitor(this);
      checkType(paramType, arg.v2, arg.v1.range());
      resolvedArguments.add(arg.v1);
    }

    Type returnType = m.returnType.acceptVisitor(this);

    return tuple(
        new Expression.MethodCall(self.v1, new Ref<>(m), resolvedArguments, that.range),
        returnType);
  }

  /** Returns null if we don't resolve this to System.out.println. */
  private Tuple2<Expression, Type> systemOutPrintlnHackForSelf(Expression.MethodCall that) {
    if (!(that.self instanceof Expression.FieldAccess)) {
      return null;
    }
    Expression.FieldAccess fieldAccess = (Expression.FieldAccess) that.self;
    if (!(fieldAccess.self instanceof Expression.Variable)) {
      return null;
    }
    Expression.Variable system = (Expression.Variable) fieldAccess.self;
    if (!system.var.name().equals("System")
        || locals.lookup("System").isPresent()
        || fields.lookup("System").isPresent()) {
      return null;
    }
    // "System" is not defined in the current scope somewhere
    // We know that the expression looks like this now: System.<foo>.<methodname>()
    if (!fieldAccess.field.name().equals("out") || !that.method.name().equals("println")) {
      return null;
    }

    return tuple(Expression.ReferenceTypeLiteral.systemOut(fieldAccess.range()), Type.SYSTEM_OUT);
  }

  @NotNull
  private static Optional<Class> isClass(Type type) {
    if (type.dimension > 0 || !(type.basicType.def instanceof Class)) {
      return Optional.empty();
    }

    return Optional.of((Class) type.basicType.def);
  }

  @Override
  public Tuple2<Expression, Type> visitFieldAccess(Expression.FieldAccess that) {
    Tuple2<Expression, Type> self = that.self.acceptVisitor(this);

    Optional<Class> definingClass = isClass(self.v2);

    if (!definingClass.isPresent()) {
      throw new SemanticError(that.range(), "Only classes have fields");
    }

    Optional<Field> fieldOpt =
        definingClass
            .get()
            .fields
            .stream()
            .filter(f -> f.name().equals(that.field.name()))
            .findFirst();

    if (!fieldOpt.isPresent()) {
      throw new SemanticError(
          that.range(),
          "Class '" + definingClass.get().name() + "' has no field " + that.field.name());
    }

    Field field = fieldOpt.get();
    field.definingClass = new Ref<>(definingClass.get());
    Type returnType = field.type.acceptVisitor(this);
    return tuple(new Expression.FieldAccess(self.v1, new Ref<>(field), that.range), returnType);
  }

  @Override
  public Tuple2<Expression, Type> visitArrayAccess(Expression.ArrayAccess that) {
    Tuple2<Expression, Type> arr = that.array.acceptVisitor(this);
    Tuple2<Expression, Type> idx = that.index.acceptVisitor(this);

    checkType(Type.INT, idx.v2, idx.v1.range());
    checkElementTypeIsNotVoid(arr.v2, arr.v1.range());
    checkIsArrayType(arr.v2, arr.v1.range());

    Type returnType = new Type(arr.v2.basicType, arr.v2.dimension - 1, arr.v2.range());
    return tuple(new Expression.ArrayAccess(arr.v1, idx.v1, that.range()), returnType);
  }

  @Override
  public Tuple2<Expression, Type> visitNewObject(NewObject that) {
    Optional<BasicType> optDef = types.lookup(that.class_.name());
    if (!optDef.isPresent()) {
      throw new SemanticError(that.range(), "Type is not present");
    }
    // This actually should never happen to begin with..
    // The parser will not produce such a type.
    if (!(optDef.get() instanceof Class)) {
      throw new SemanticError(that.range(), "Only reference types can be allocated with new.");
    }

    // Ref is invariant in its first type parameter, for good reason.
    // Unfortunately that means we have to duplicate refs here.
    Ref<BasicType> asBasicType = new Ref<>(optDef.get());
    Ref<Class> asClass = new Ref<>((Class) optDef.get());
    Type returnType = new Type(asBasicType, 0, optDef.get().range());
    return tuple(new NewObject(asClass, that.range()), returnType);
  }

  @Override
  public Tuple2<Expression, Type> visitNewArray(NewArray that) {
    Type type = that.type.acceptVisitor(this);
    Tuple2<Expression, Type> size = that.size.acceptVisitor(this);

    checkType(Type.INT, size.v2, size.v1.range());
    checkElementTypeIsNotVoid(type, that.range());

    // here than in the parser, e.g. NewArray.type should denote the type of the elements of the new array
    Type returnType = new Type(type.basicType, type.dimension + 1, type.range());
    return tuple(new NewArray(type, size.v1, that.range()), returnType);
  }

  /**
   * The Expression.Variable case is rather interesting.
   *
   * <p>We try to resolve it as a local variable or parameter at first, by looking up its name in
   * locals. If this was unsuccessful, we try to resolve the variable as a FieldAccess to this.,
   * e.g:
   *
   * <p>class A { int x; public void f() { x = 4; } }
   *
   * <p>will first try to resolve x as a local variable and only then resolve it to a field access
   * to the enclosing class with an implicit this.
   *
   * <p>This is the only place in the analyzer where we rewrite AST nodes to different types!
   */
  @Override
  public Tuple2<Expression, Type> visitVariable(Expression.Variable that) {
    Optional<Definition> varOpt = locals.lookup(that.var.name());
    if (varOpt.isPresent()) {
      // is it a local var decl or a parameter?
      if (varOpt.get() instanceof BlockStatement.Variable) {
        Statement.Variable decl = (Statement.Variable) varOpt.get();
        return tuple(new Expression.Variable(new Ref<>(decl), that.range), decl.type);
      } else if (varOpt.get() instanceof Parameter) {
        Parameter p = (Parameter) varOpt.get();
        // Because of a hack where we represent main's parameter with type void, we have to check
        // if the expression is a variable of type void.
        checkElementTypeIsNotVoid(p.type, p.range());
        return tuple(new Expression.Variable(new Ref<>(p), that.range), p.type);
      } else {
        // We must have put something else into locals, this mustn't happen.
        assert false;
      }
    }

    // So it wasn't a local var... Maybe it was a field of the enclosing class
    Optional<Field> fieldOpt = fields.lookup(that.var.name());

    if (fieldOpt.isPresent() && !currentMethod.isStatic) {
      // Analyze as if there was a preceding 'this.' in front of the variable
      // The field is there, so we can let errors pass through without causing confusion
      return new Expression.FieldAccess(THIS_EXPR, new Ref<>(fieldOpt.get()), that.range)
          .acceptVisitor(this);
    }

    throw new SemanticError(that.range(), "Variable '" + that.var.name() + "' is not in scope.");
  }

  @Override
  public Tuple2<Expression, Type> visitBooleanLiteral(BooleanLiteral that) {
    return tuple(that, Type.BOOLEAN);
  }

  @Override
  public Tuple2<Expression, Type> visitIntegerLiteral(IntegerLiteral that) {

    if (Ints.tryParse(that.literal, 10) == null) {
      // insert range
      throw new SemanticError(
          that.range(), "The literal '" + that.literal + "' is not a valid 32-bit number");
    }

    return tuple(that, Type.INT);
  }

  @Override
  public Tuple2<Expression, Type> visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    if (that.name().equals("null")) {
      return tuple(that, Type.ANY_REF);
    }
    assert that.name().equals("this");

    if (currentMethod.isStatic) {
      throw new SemanticError(
          that.range(), "Cannot access 'this' in a static method " + that.name());
    }

    return tuple(that, new Type(new Ref<>(currentClass), 0, currentClass.range()));
  }
}
