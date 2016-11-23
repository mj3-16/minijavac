package minijava.semantic;

import com.google.common.primitives.Ints;
import java.util.Optional;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Expression.BooleanLiteral;
import minijava.ast.Expression.IntegerLiteral;
import minijava.ast.Expression.NewArray;
import minijava.ast.Expression.NewObject;
import minijava.ast.LocalVariable;
import minijava.ast.Statement.ExpressionStatement;
import minijava.util.SourceRange;
import org.jetbrains.annotations.NotNull;

/**
 * Performs name analysis for a Program.
 *
 * <p>It does so by first collecting all class definitions (including field and method names) in a
 * first pass, then will resolve names and perform type checks in a tree traversing manner.
 *
 * <p>Expressions aren't only resolved, but also their types are inferred. This is why we do it in
 * lock-step by returning a Pair.
 */
public class SemanticAnalyzer
    implements Program.Visitor<Void>,
        Class.Visitor<Void>,
        Field.Visitor<Void>,
        Type.Visitor<Void>,
        Method.Visitor<Void>,
        BlockStatement.Visitor<Void>,
        Expression.Visitor<Expression> {
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
  private SymbolTable<LocalVariable> locals = new SymbolTable<>();
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
  public Void visitProgram(Program that) {
    mainMethod = null;
    // collect all types first (throws if duplicates exist)
    this.types = that.acceptVisitor(new TypeCollector());
    for (Class c : that.declarations) {
      c.acceptVisitor(this);
    }
    if (mainMethod == null) {
      throw new SemanticError(that.range(), "No main method defined");
    }
    return null;
  }

  /**
   * 1. Collect and resolve field references, checking for duplicates. 2. Collect and resolve method
   * references, including bodies, checking for duplicates.
   */
  @Override
  public Void visitClass(Class that) {
    currentClass = that;
    // fields in current class
    fields = new SymbolTable<>();
    fields.enterScope();
    for (Field f : that.fields) {
      if (fields.inCurrentScope(f.name())) {
        throw new SemanticError(
            f.range(), "Field '" + f.name() + "' is already defined in this scope");
      }
      fields.insert(f.name(), f);
      f.acceptVisitor(this);
    }

    // We only need the symbol table for methods for checking for duplicates.
    // At call sites, the called method is resolved by looking it up in the
    // body of the class type left to the dot.
    SymbolTable<Method> methods = new SymbolTable<>();
    methods.enterScope();
    for (Method m : that.methods) {
      if (methods.inCurrentScope(m.name())) {
        throw new SemanticError(
            m.range(), "Method '" + m.name() + "' is already defined in this scope");
      }
      methods.insert(m.name(), m);
      currentMethod = m;
      m.acceptVisitor(this);
    }

    return null;
  }

  /** Fields need to have their type resolved. Also that type musn't be void. */
  @Override
  public Void visitField(Field that) {
    that.definingClass = new Ref<>(currentClass);
    that.type.acceptVisitor(this);
    checkElementTypeIsNotVoid(that.type, that.type.range());
    return null;
  }

  /**
   * Types are always arrays of a certain element type BasicType, which must be present in the
   * pre-collected types SymbolTable.
   */
  @Override
  public Void visitType(Type that) {
    if (that.basicType.def != null) {
      // We already analyzed this type
      return null;
    }
    String typeName = that.basicType.name();
    Optional<BasicType> optDef = types.lookup(typeName);
    if (!optDef.isPresent()) {
      throw new SemanticError(that.range(), "Type '" + typeName + "' is not defined");
    }
    that.basicType.def = optDef.get();
    return null;
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
  public Void visitMethod(Method that) {
    that.definingClass = new Ref<>(currentClass);

    // 1.
    that.returnType.acceptVisitor(this);

    // 2. Check types and transform parameters
    // main gets special treatment. We replace its parameter type by void, to make it unusable.
    if (that.isStatic) {
      // We begin with some sanity checks that the parser should already have verified.
      if (!that.name().equals("main")) {
        throw new SemanticError(that.range(), "Static methods must be named main.");
      }
      if (that.parameters.size() != 1) {
        throw new SemanticError(that.range(), "The main method must have exactly one parameter.");
      }

      LocalVariable p = that.parameters.get(0);
      Type type = p.type;
      if (!type.basicType.name().equals("String") || type.dimension != 1) {
        throw new SemanticError(
            that.range(), "The main method's parameter must have type String[].");
      }
      checkType(Type.VOID, that.returnType, that.returnType.range());

      // Here begins the actual interesting part where things can go wrong.
      if (mainMethod != null) {
        throw new SemanticError(
            that.range(), "There is already a main method defined at " + mainMethod.range());
      }
      mainMethod = that;
      // Here we save the String[] parameter as type void instead.
      // This is a hack so that we can't access it in the body, but it leads to confusing error messages.
      // An alternative would be to define another special type like void for better error messages.
      that.parameters.set(0, new LocalVariable(Type.VOID, p.name(), p.range()));
    } else { // !isStatic
      for (LocalVariable p : that.parameters) {
        p.type.acceptVisitor(this);
        checkElementTypeIsNotVoid(p.type, p.range());
      }
    }

    // We collect local variables into a fresh symboltable. We need these later on,
    // when we try to resolve Variables via FieldAccess on this. in method bodies.
    locals = new SymbolTable<>();
    locals.enterScope();

    // check for parameters with same name
    for (LocalVariable p : that.parameters) {
      if (locals.lookup(p.name()).isPresent()) {
        throw new SemanticError(
            p.range(), "There is already a parameter defined with name '" + p.name() + "'");
      }
      locals.insert(p.name(), p);
    }

    hasReturned = false;
    that.body.acceptVisitor(this);
    locals.leaveScope();
    // Check if we returned on each possible path and if not, that the return type was void.
    if (!hasReturned) {
      // Check if we implicitly returned
      checkType(Type.VOID, that.returnType, that.returnType.range());
    }
    return null;
  }

  /** Straightforward. Remember to open a new scope for the block. */
  @Override
  public Void visitBlock(Block that) {
    locals.enterScope();
    // This also picks up local var decls into @locals@ on the go
    that.statements.forEach(s -> s.acceptVisitor(this));
    locals.leaveScope();
    return null;
  }

  @Override
  public Void visitEmpty(Statement.Empty that) {
    return null;
  }

  /**
   * 1. check that the condition expression is well-typed and returns a boolean 2. visit then 3.
   * visit else
   *
   * <p>Also remember to open and close Scopes. The hasReturned flag also deserves some special
   * mention.
   */
  @Override
  public Void visitIf(Statement.If that) {
    that.condition = that.condition.acceptVisitor(this);
    checkType(Type.BOOLEAN, that.condition.type(), that.condition.range());

    // Save the old returned flag. If this was true, hasReturned will be true afterwards,
    // no matter what the results of the two branches are.
    boolean oldHasReturned = hasReturned;
    hasReturned = false; // This is actually not necessary, but let's be explicit

    locals.enterScope();
    // then might be a block and therefore enters and leaves another subscope, but that's ok
    that.then.acceptVisitor(this);
    locals.leaveScope();

    // Save the result from then
    boolean thenHasReturned = hasReturned;
    hasReturned = false;

    if (that.else_.isPresent()) {
      locals.enterScope();
      that.else_.get().acceptVisitor(this);
      locals.leaveScope();
    } // else hasReturned would also be false (we could always fall through the if statement)

    boolean elseHasReturned = hasReturned;

    // Either we returned before this is even executed or we returned in both branches. Otherwise we didn't return.
    hasReturned = oldHasReturned || thenHasReturned && elseHasReturned;
    return null;
  }

  @Override
  public Void visitExpressionStatement(ExpressionStatement that) {
    // We don't care for the expressions type, as long as there is one
    // ... although we probably want to safe types in (Typed-)Ref later on
    that.expression = that.expression.acceptVisitor(this);
    return null;
  }

  /** Analogous to If. */
  @Override
  public Void visitWhile(Statement.While that) {
    that.condition = that.condition.acceptVisitor(this);
    checkType(Type.BOOLEAN, that.condition.type(), that.condition.range());

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
    that.body.acceptVisitor(this);
    locals.leaveScope();

    hasReturned = oldHasReturned;

    return null;
  }

  /**
   * If the expression to return is present, we visit it and make sure that the type matches the
   * current methods return type. Otherwise, the current methods return type must be void.
   */
  @Override
  public Void visitReturn(Statement.Return that) {
    currentMethod.returnType.acceptVisitor(this);
    Type returnType = currentMethod.returnType;
    if (that.expression.isPresent()) {
      if (!currentMethod.equals(mainMethod)) {
        checkElementTypeIsNotVoid(returnType, returnType.range());
        Expression expr = that.expression.get().acceptVisitor(this);
        checkType(returnType, expr.type(), expr.range());
        that.expression = Optional.of(expr);
        hasReturned = true;
        return null;
      } else {
        throw new SemanticError(
            that.expression.get().range(), "Returning a value is not valid in main method");
      }
    } else {
      checkType(Type.VOID, returnType, returnType.range());
      hasReturned = true;
      return null;
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
  public Void visitVariable(BlockStatement.Variable that) {
    that.type.acceptVisitor(this);
    checkElementTypeIsNotVoid(that.type, that.type.range());
    if (locals.lookup(that.name()).isPresent()) {
      throw new SemanticError(that.range(), "Cannot redefine '" + that.name() + "'");
    }

    if (that.rhs.isPresent()) {
      Expression ret = that.rhs.get().acceptVisitor(this);
      checkType(that.type, ret.type(), ret.range());
      that.rhs = Optional.of(ret);
    }

    locals.insert(that.name(), that);
    return null;
  }

  /**
   * This is mostly interesting from a type-checking perspective. Look into the respective switch
   * cases.
   */
  @Override
  public Expression visitBinaryOperator(Expression.BinaryOperator that) {
    Expression left = that.left.acceptVisitor(this);
    Expression right = that.right.acceptVisitor(this);

    Type resultType = null; // The result of that switch statement
    switch (that.op) {
      case ASSIGN:
        // make sure that we can actually assign something to left, e.g.
        // it should be a fieldaccess or variableexpression.
        if (!(left instanceof Expression.FieldAccess)
            && !(left instanceof Expression.Variable)
            && !(left instanceof Expression.ArrayAccess)) {
          throw new SemanticError(left.range(), "Expression is not assignable");
        }
        // Also left.type() must match right.type()
        checkType(
            left.type(),
            right.type(),
            right.range()); // This would be broken for type Any, but null can't be assigned to
        // The result type of the assignment expression is just left.type()
        resultType = left.type();
        break;
      case PLUS:
      case MINUS:
      case MULTIPLY:
      case DIVIDE:
      case MODULO:
        // int -> int -> int
        // so, check that left and left.type() is int
        checkType(Type.INT, left.type(), left.range());
        checkType(Type.INT, right.type(), right.range());
        // Then we can just reuse left's type
        resultType = left.type();
        break;
      case OR:
      case AND:
        // bool -> bool -> bool
        checkType(Type.BOOLEAN, left.type(), left.range());
        checkType(Type.BOOLEAN, right.type(), right.range());
        resultType = left.type();
        break;
      case EQ:
      case NEQ:
        // T -> T -> bool
        // The Ts have to match
        checkType(left.type(), right.type(), right.range());
        resultType = Type.BOOLEAN;
        break;
      case LT:
      case LEQ:
      case GT:
      case GEQ:
        // int -> int -> bool
        checkType(Type.INT, left.type(), left.range());
        checkType(Type.INT, right.type(), right.range());
        resultType = Type.BOOLEAN;
        break;
    }

    that.left = left;
    that.right = right;
    that.type = resultType;

    return that;
  }

  @Override
  public Expression visitUnaryOperator(Expression.UnaryOperator that) {

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

    Expression expr = that.expression.acceptVisitor(this);
    checkType(expected, expr.type(), expr.range());
    that.expression = expr;
    that.type = expected;

    return that;
  }

  @NotNull
  private Expression handleNegativeIntegerLiterals(
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
    lit.type = Type.INT;
    that.type = Type.INT;
    return that;
  }

  @Override
  public Expression visitMethodCall(Expression.MethodCall that) {

    // This will be null if this wasn't a call to System.out.println
    Expression self = systemOutPrintlnHackForSelf(that);
    if (self == null) {
      // That was not a call matching System.out.println(). So we procede regularly
      self = that.self.acceptVisitor(this);
    }
    that.self = self;

    Optional<Class> definingClassOpt = asClass(self.type());

    if (!definingClassOpt.isPresent()) {
      throw new SemanticError(that.range(), "Only classes have methods");
    }
    Class definingClass = definingClassOpt.get();

    // This will find the method in the class body of the self object
    Optional<Method> methodOpt =
        definingClass.methods.stream().filter(m -> m.name().equals(that.method.name())).findFirst();

    if (!methodOpt.isPresent()) {
      throw new SemanticError(
          that.range(),
          "Class '" + definingClass.name() + "' has no method '" + that.method.name() + "'");
    }

    Method m = methodOpt.get();
    m.definingClass = new Ref<>(definingClass);

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
    for (int i = 0; i < m.parameters.size(); ++i) {
      LocalVariable p = m.parameters.get(i);
      p.type.acceptVisitor(this);
      Expression arg = that.arguments.get(i).acceptVisitor(this);
      checkType(p.type, arg.type(), arg.range());
    }

    m.returnType.acceptVisitor(this);
    that.type = m.returnType;

    return that;
  }

  /** Returns null if we don't resolve this to System.out.println. */
  private Expression systemOutPrintlnHackForSelf(Expression.MethodCall that) {
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

    return Expression.ReferenceTypeLiteral.systemOut(fieldAccess.range());
  }

  @NotNull
  private static Optional<Class> asClass(Type type) {
    if (type.dimension > 0 || !(type.basicType.def instanceof Class)) {
      return Optional.empty();
    }

    return Optional.of((Class) type.basicType.def);
  }

  @Override
  public Expression visitFieldAccess(Expression.FieldAccess that) {
    Expression self = that.self.acceptVisitor(this);

    Optional<Class> definingClassOpt = asClass(self.type());

    if (!definingClassOpt.isPresent()) {
      throw new SemanticError(that.range(), "Only classes have fields");
    }
    Class definingClass = definingClassOpt.get();

    Optional<Field> fieldOpt =
        definingClass.fields.stream().filter(f -> f.name().equals(that.field.name())).findFirst();

    if (!fieldOpt.isPresent()) {
      throw new SemanticError(
          that.range(), "Class '" + definingClass.name() + "' has no field " + that.field.name());
    }

    Field field = fieldOpt.get();
    field.definingClass = new Ref<>(definingClass);
    field.type.acceptVisitor(this);
    that.self = self;
    that.field.def = field;
    that.type = field.type;
    return that;
  }

  @Override
  public Expression visitArrayAccess(Expression.ArrayAccess that) {
    Expression arr = that.array.acceptVisitor(this);
    Expression idx = that.index.acceptVisitor(this);

    checkType(Type.INT, idx.type(), idx.range());
    checkElementTypeIsNotVoid(arr.type(), arr.range());
    checkIsArrayType(arr.type(), arr.range());

    that.array = arr;
    that.index = idx;

    that.type = new Type(arr.type().basicType, arr.type().dimension - 1, arr.type().range());
    return that;
  }

  @Override
  public Expression visitNewObject(NewObject that) {
    Optional<BasicType> optDef = types.lookup(that.class_.name());
    if (!optDef.isPresent()) {
      throw new SemanticError(that.range(), "Type is not present");
    }
    // This actually should never happen to begin with..
    // The parser will not produce such a type.
    if (!(optDef.get() instanceof Class)) {
      throw new SemanticError(that.range(), "Only reference types can be allocated with new.");
    }
    Class class_ = (Class) optDef.get();

    // Ref is invariant in its first type parameter, for good reason.
    // Unfortunately that means we have to duplicate refs here.
    Ref<BasicType> asBasicType = new Ref<>(class_);
    that.type = new Type(asBasicType, 0, optDef.get().range());
    that.class_.def = class_;
    return that;
  }

  @Override
  public Expression visitNewArray(NewArray that) {
    that.elementType.acceptVisitor(this);
    Expression size = that.size.acceptVisitor(this);

    checkType(Type.INT, size.type(), size.range());
    checkElementTypeIsNotVoid(that.elementType, that.range());

    that.size = size;

    // here than in the parser, e.g. NewArray.type should denote the type of the elements of the new array
    that.type =
        new Type(
            that.elementType.basicType, that.elementType.dimension + 1, that.elementType.range());
    return that;
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
  public Expression visitVariable(Expression.Variable that) {
    Optional<LocalVariable> varOpt = locals.lookup(that.var.name());
    if (varOpt.isPresent()) {
      // is it a local var decl or a parameter?
      LocalVariable p = varOpt.get();
      // Because of a hack where we represent main's parameter with type void, we have to check
      // if the expression is a variable of type void.
      checkElementTypeIsNotVoid(p.type, p.range());
      that.var.def = p;
      that.type = p.type;
      return that;
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
  public Expression visitBooleanLiteral(BooleanLiteral that) {
    that.type = Type.BOOLEAN;
    return that;
  }

  @Override
  public Expression visitIntegerLiteral(IntegerLiteral that) {

    if (Ints.tryParse(that.literal, 10) == null) {
      // insert range
      throw new SemanticError(
          that.range(), "The literal '" + that.literal + "' is not a valid 32-bit number");
    }
    that.type = Type.INT;
    return that;
  }

  @Override
  public Expression visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    if (that.name().equals("null")) {
      that.type = Type.ANY_REF;
      return that;
    }
    assert that.name().equals("this");

    if (currentMethod.isStatic) {
      throw new SemanticError(
          that.range(), "Cannot access 'this' in a static method " + that.name());
    }

    that.type = new Type(new Ref<>(currentClass), 0, currentClass.range());
    return that;
  }
}
