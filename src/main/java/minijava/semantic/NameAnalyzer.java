package minijava.semantic;

import static org.jooq.lambda.tuple.Tuple.tuple;

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
import minijava.parser.Parser;
import minijava.util.SourceRange;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple2;

class NameAnalyzer
    implements Program.Visitor<Nameable, Program<Ref>>,
        Class.Visitor<Nameable, Class<Ref>>,
        Field.Visitor<Nameable, Field<Ref>>,
        Type.Visitor<Nameable, Type<Ref>>,
        Method.Visitor<Nameable, Method<Ref>>,
        BlockStatement.Visitor<Nameable, BlockStatement<Ref>>,
        Expression.Visitor<Nameable, Tuple2<Expression<Ref>, Type<Ref>>> {

  // contains BasicType and Class<? extends Nameable> Definitions
  // TODO: make them both derive from a common interface type
  private SymbolTable<Definition> types = new SymbolTable<>();

  // TODO: make both BlockStatement.Variable and Parameter derive from a common interface type
  private SymbolTable<Definition> variables = new SymbolTable<>();
  private SymbolTable<Field<? extends Nameable>> fields = new SymbolTable<>();
  private SymbolTable<Method<? extends Nameable>> methods = new SymbolTable<>();

  @Override
  public Program<Ref> visitProgram(Program<? extends Nameable> that) {
    // collect all types first (throws if duplicates exist)
    this.types = that.acceptVisitor(new TypeCollector());
    List<Class<Ref>> refClasses = new ArrayList<>(that.declarations.size());
    for (Class<? extends Nameable> c : that.declarations) {
      Class<Ref> refClass = c.acceptVisitor(this);
      refClasses.add(refClass);
    }
    return new Program<>(refClasses, that.range());
  }

  @Override
  public Class<Ref> visitClassDeclaration(Class<? extends Nameable> that) {
    // fields in current class
    fields = new SymbolTable<>();
    fields.enterScope();
    List<Field<Ref>> newFields = new ArrayList<>(that.fields.size());
    for (Field<? extends Nameable> f : that.fields) {
      if (fields.inCurrentScope(f.name())) {
        throw new SemanticError();
      }
      fields.insert(f.name(), f);
      Field<Ref> field = f.acceptVisitor(this);
      newFields.add(field);
    }

    // methods in current class
    methods = new SymbolTable<>();
    methods.enterScope();

    // First pick up all method declarations
    for (Method<? extends Nameable> m : that.methods) {
      if (methods.inCurrentScope(m.name())) {
        throw new SemanticError();
      }
      methods.insert(m.name(), m);
    }

    List<Method<Ref>> newMethods = new ArrayList<>(that.methods.size());
    for (Method<? extends Nameable> m : that.methods) {
      Method<Ref> method = m.acceptVisitor(this);
      newMethods.add(method);
    }

    return new Class<>(that.name(), newFields, newMethods, that.range());
  }

  @Override
  public Field<Ref> visitField(Field<? extends Nameable> that) {
    Type<Ref> type = that.type.acceptVisitor(this);
    return new Field<>(type, that.name(), that.range());
  }

  @Override
  public Type<Ref> visitType(Type<? extends Nameable> that) {
    String typeName = that.typeRef.name();
    Optional<Definition> optDef = types.lookup(typeName);
    if (!optDef.isPresent()) {
      throw new SemanticError("Type " + typeName + " is not defined");
    }
    return new Type<>(new Ref(optDef.get()), that.dimension, that.range());
  }

  @Override
  public Method<Ref> visitMethod(Method<? extends Nameable> that) {
    Type<Ref> returnType = that.returnType.acceptVisitor(this);
    // check types and transform parameters
    List<Parameter<Ref>> newParams = new ArrayList<>(that.parameters.size());
    for (Parameter<? extends Nameable> p : that.parameters) {
      Type<Ref> type = p.type.acceptVisitor(this);
      newParams.add(new Parameter<>(type, p.name(), p.range()));
    }

    // go from class scope to method scope
    variables.enterScope();

    // check for parameters with same name
    for (Parameter<Ref> p : newParams) {
      if (variables.inCurrentScope(p.name())) {
        throw new SemanticError();
      }
      variables.insert(p.name(), p);
    }
    Block<Ref> block = (Block<Ref>) that.body.acceptVisitor(this);
    // go back to class scope
    variables.leaveScope();
    return new Method<>(that.isStatic, returnType, that.name(), newParams, block, that.range());
  }

  @Override
  public Block<Ref> visitBlock(Block<? extends Nameable> that) {
    variables.enterScope();
    List<BlockStatement<Ref>> newStatements = new ArrayList<>(that.statements.size());
    for (BlockStatement<? extends Nameable> s : that.statements) {
      // This also picks up local var decls into @variables@ on the go
      newStatements.add(s.acceptVisitor(this));
    }
    variables.leaveScope();
    return new Block<>(newStatements, that.range());
  }

  @Override
  public Statement<Ref> visitEmpty(Statement.Empty<? extends Nameable> that) {
    return new Statement.Empty<>(that.range());
  }

  @Override
  public Statement<Ref> visitIf(Statement.If<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> cond = that.condition.acceptVisitor(this);
    Expression<Ref> newCondition = cond.v1;
    Type<Ref> conditionType = cond.v2;

    // TODO - Type: check that conditionType is plain bool

    variables.enterScope();
    // then might be a block and therefore enters and leaves another subscope, but that's ok
    // TODO - Names: I don't think we should enter a new scope here. In case it's not block,
    //               new names cannot be defined (which is reasonable). Otherwise we open
    //               a scope anyway.
    // Try commenting out this example:
    //
    //     int i = 0;
    //     if (true) {
    //       int i = 5; // nope
    //     }
    //
    // Although this should also be invalid:
    //
    //     if (true) {
    //       int i = 5;
    //     }
    //     i++;
    //
    // I hate Java
    Statement<Ref> newThen = (Statement<Ref>) that.then.acceptVisitor(this);
    variables.leaveScope();

    Statement<Ref> newElse = null;
    if (that.else_.isPresent()) {
      variables.enterScope();
      newElse = (Statement<Ref>) that.else_.get().acceptVisitor(this);
      variables.leaveScope();
    }
    return new Statement.If<>(newCondition, newThen, newElse, that.range());
  }

  @Override
  public Statement<Ref> visitExpressionStatement(ExpressionStatement<? extends Nameable> that) {
    // We don't care for the expressions type, as long as there is one
    // ... although we probably want to safe types in (Typed-)Ref later on
    Expression<Ref> expr = that.expression.acceptVisitor(this).v1;
    return new ExpressionStatement<>(expr, that.range());
  }

  @Override
  public Statement<Ref> visitWhile(Statement.While<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> cond = that.condition.acceptVisitor(this);

    // TODO - Types: check that cond.v2 is plain bool

    variables.enterScope();
    // if body is a block it might enter another subscope (see visitBlock) but that doesn't really matter)
    Statement<Ref> body = (Statement<Ref>) that.body.acceptVisitor(this);
    variables.leaveScope();
    return new Statement.While<>(cond.v1, body, that.range());
  }

  @Override
  public Statement<Ref> visitReturn(Statement.Return<? extends Nameable> that) {
    if (that.expression.isPresent()) {
      Tuple2<Expression<Ref>, Type<Ref>> expr = that.expression.get().acceptVisitor(this);

      // TODO - Types: Find a way to compare expr.v2 with the methods return type
      //               I'd probably just save the declared return type in a field
      //               and compare with that

      return new Statement.Return<>(expr.v1, that.range());
    }
    return new Statement.Return<>(null, that.range());
  }

  @Override
  public BlockStatement<Ref> visitVariable(BlockStatement.Variable<? extends Nameable> that) {
    Type<Ref> variableType = that.type.acceptVisitor(this);
    if (variables.inCurrentScope(that.name())) {
      throw new SemanticError();
    }
    Tuple2<Expression<Ref>, Type<Ref>> rhs = that.rhs.acceptVisitor(this);

    // TODO - Types: Check that rhs.v2 matches variableType

    BlockStatement.Variable<Ref> var =
        new BlockStatement.Variable<>(variableType, that.name(), rhs.v1, that.range());
    variables.insert(that.name(), var);
    return var;
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitBinaryOperator(
      Expression.BinaryOperator<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> left = that.left.acceptVisitor(this);
    Tuple2<Expression<Ref>, Type<Ref>> right = that.right.acceptVisitor(this);

    // TODO - Types: Check that that.op can be applied to left.v2 and right.v2
    //               Example:      +                        int        bool
    //                        Obviously is incompatible
    //
    // Here is probably the right place to switch over that.op and handle all
    // cases... Let me pregenerate something

    Type<Ref> resultType = null; // The result of that switch statement
    switch (that.op) {
      case ASSIGN:
        // make sure that we can actually assign something to left.v1, e.g.
        // it should be a fieldaccess or variableexpression.
        // Also left.v2 must match right.v2

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

        // Then we can just reuse left's type
        resultType = left.v2;
        break;
      case OR:
      case AND:
        // bool -> bool -> bool
        // dito
        resultType = left.v2;
        break;
      case EQ:
      case NEQ:
        // T -> T -> bool
        // For reference types, it doesn't matter what actual type they are,
        // as long as they're both references (e.g. Foo foo; Bar bar; foo == bar is OK)
        resultType = new Type<>(new Ref(BasicType.BOOLEAN), 0, SourceRange.FIRST_CHAR);
        break;
      case LT:
      case LEQ:
      case GT:
      case GEQ:
        // int -> int -> bool
        resultType = new Type<>(new Ref(BasicType.BOOLEAN), 0, SourceRange.FIRST_CHAR);
        break;
    }

    return tuple(
        new Expression.BinaryOperator<>(that.op, left.v1, right.v1, that.range()), resultType);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitUnaryOperator(
      Expression.UnaryOperator<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> expr = that.expression.acceptVisitor(this);

    // TODO - Types: You know what to do
    switch (that.op) {
      case NOT:
        // bool -> bool
        break;
      case NEGATE:
        // int -> int
        break;
    }

    return tuple(new Expression.UnaryOperator<>(that.op, expr.v1, that.range()), expr.v2);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitMethodCall(
      Expression.MethodCall<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> self = that.self.acceptVisitor(this);

    Optional<Class<Nameable>> definingClass = isTypeWithMembers(self.v2);

    if (!definingClass.isPresent()) {
      // TODO - Names
      throw new SemanticError("only classes have methods");
    }

    Optional<Method<Nameable>> methodOpt =
        definingClass
            .get()
            .methods
            .stream()
            .filter(m -> m.name().equals(that.method.name()))
            .findFirst();

    if (!methodOpt.isPresent()) {
      // TODO - Names
      throw new SemanticError("Class blah had no method blah");
    }

    Method<Nameable> m = methodOpt.get();

    // TODO - Types: Now that we have the method, we have access to its type.
    //               Check that argument types match declared parameter types!

    if (m.parameters.size() != that.arguments.size()) {
      // TODO - Types: Do a better job than me
      throw new SemanticError(
          "Number of declared parameters and actual number of arguments of the call mismatched");
    }

    List<Expression<Ref>> resolvedArguments = new ArrayList<>(that.arguments.size());
    for (int i = 0; i < m.parameters.size(); ++i) {
      Type<Ref> paramType = m.parameters.get(i).type.acceptVisitor(this);
      Tuple2<Expression<Ref>, Type<Ref>> arg = that.arguments.get(i).acceptVisitor(this);

      // TODO - Types: check that paramType is the same as arg.v2

      resolvedArguments.add(arg.v1);
    }

    Type<Ref> returnType = m.returnType.acceptVisitor(this);

    return tuple(
        new Expression.MethodCall<>(self.v1, new Ref(m), resolvedArguments, that.range),
        returnType);
  }

  @NotNull
  private static Optional<Class<Nameable>> isTypeWithMembers(Type<Ref> type) {
    if (type.dimension > 0 || !(type.typeRef.def instanceof Class)) {
      return Optional.empty();
    }

    return Optional.of((Class<Nameable>) type.typeRef.def);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitFieldAccess(
      Expression.FieldAccess<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> self = that.self.acceptVisitor(this);

    Optional<Class<Nameable>> definingClass = isTypeWithMembers(self.v2);

    if (!definingClass.isPresent()) {
      // TODO - Names
      throw new SemanticError("only classes have fields");
    }

    Optional<Field<Nameable>> fieldOpt =
        definingClass
            .get()
            .fields
            .stream()
            .filter(f -> f.name().equals(that.field.name()))
            .findFirst();

    if (!fieldOpt.isPresent()) {
      // TODO - Names
      throw new SemanticError("Class blah had no field blah");
    }

    Field<Nameable> field = fieldOpt.get();
    Type<Ref> returnType = field.type.acceptVisitor(this);
    return tuple(new Expression.FieldAccess<>(self.v1, new Ref(field), that.range), returnType);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitArrayAccess(
      Expression.ArrayAccess<? extends Nameable> that) {
    Tuple2<Expression<Ref>, Type<Ref>> arr = that.array.acceptVisitor(this);
    Tuple2<Expression<Ref>, Type<Ref>> idx = that.index.acceptVisitor(this);

    // TODO - Types:
    // - idx.v2 is of plain type int (e.g. also dimension == 0)
    // - arr.v2 is an array type (dimension > 0, already did that below, because I need the return type)
    // - arr.v2 is not an array of void

    if (arr.v2.dimension == 0) {
      // TODO - Names
      throw new SemanticError("can only index array types");
    }

    Type<Ref> returnType = new Type<>(arr.v2.typeRef, arr.v2.dimension - 1, arr.v2.range());
    return tuple(new Expression.ArrayAccess<>(arr.v1, idx.v1, that.range()), returnType);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitNewObject(NewObject<? extends Nameable> that) {
    Optional<Definition> optDef = types.lookup(that.type.name());
    if (!optDef.isPresent()) {
      throw new SemanticError();
    }
    // This actually should never happen to begin with..
    // The parser will not produce such a type.
    assert optDef.get() instanceof Class;

    Ref ref = new Ref(optDef.get());
    Type<Ref> returnType = new Type<>(ref, 0, ((Class) optDef.get()).range());
    return tuple(new NewObject<>(new Ref(optDef.get()), that.range()), returnType);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitNewArray(NewArray<? extends Nameable> that) {
    Type<Ref> type = that.type.acceptVisitor(this);
    Tuple2<Expression<Ref>, Type<Ref>> size = that.size.acceptVisitor(this);

    // TODO - Types: Check that size.v2 is a plain (0-dim) int

    // TODO - discuss: The +1 will probably introduce a bug, but I find it much more intuitive to increment dimension
    // here than in the parser, e.g. NewArray.type should denote the type of the elements of the new array
    Type<Ref> returnType = new Type<>(type.typeRef, type.dimension + 1, type.range());
    return tuple(new NewArray<>(type, size.v1, that.range()), returnType);
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitVariable(
      Expression.Variable<? extends Nameable> that) {
    Optional<Definition> varOpt = variables.lookup(that.var.name());
    if (varOpt.isPresent()) {
      // TODO - Names: Handle the int x; { int x; } case?

      // is it a local var decl or a parameter?
      if (varOpt.get() instanceof BlockStatement.Variable) {
        Statement.Variable<Ref> decl = (Statement.Variable<Ref>) varOpt.get();
        return tuple(new Expression.Variable<>(new Ref(decl), that.range), decl.type);
      } else if (varOpt.get() instanceof Parameter) {
        Parameter<Ref> p = (Parameter<Ref>) varOpt.get();
        return tuple(new Expression.Variable<>(new Ref(p), that.range), p.type);
      } else {
        // We must have put something else into variables, this mustn't happen.
        assert false;
      }
    }

    // So it wasn't a local var... Maybe it was a field of the enclosing class
    Optional<Field<? extends Nameable>> fieldOpt = fields.lookup(that.var.name());

    if (fieldOpt.isPresent()) {
      // Analyze as if there was a preceding 'this.' in front of the variable
      // The field is there, so we can let errors pass through without causing confusion
      return new Expression.FieldAccess<>(Parser.THIS_EXPR, that.var, that.range)
          .acceptVisitor(this);
    }

    // TODO - Names
    throw new SemanticError("No such variable in scope");
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitBooleanLiteral(
      BooleanLiteral<? extends Nameable> that) {
    // type parameter is not used by BooleanLiteral, cast is safe
    return tuple(
        (BooleanLiteral<Ref>) that,
        new Type<>(new Ref(BasicType.BOOLEAN), 0, SourceRange.FIRST_CHAR));
  }

  @Override
  public Tuple2<Expression<Ref>, Type<Ref>> visitIntegerLiteral(
      IntegerLiteral<? extends Nameable> that) {
    // type parameter is not used by IntegerLiteral, cast is safe
    return tuple(
        (IntegerLiteral<Ref>) that, new Type<>(new Ref(BasicType.INT), 0, SourceRange.FIRST_CHAR));
  }
}
