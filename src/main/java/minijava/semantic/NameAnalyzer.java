package minijava.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.ast.Method.Parameter;

class NameAnalyzer
    implements Program.Visitor<Nameable, Program<Ref>>,
        Class.Visitor<Nameable, Class<Ref>>,
        Field.Visitor<Nameable, Field<Ref>>,
        Type.Visitor<Nameable, Type<Ref>>,
        Method.Visitor<Nameable, Method<Ref>>,
        BlockStatement.Visitor<Nameable, Statement<Ref>>,
        Expression.Visitor<Nameable, Expression<Ref>> {

  private SymbolTable types = new SymbolTable();
  private SymbolTable fieldsAndVariables = new SymbolTable();
  private SymbolTable methods = new SymbolTable();

  @Override
  public Program<Ref> visitProgram(Program<? extends Nameable> that) {
    // collect all types first (throws if duplicates exist)
    this.types = that.acceptVisitor(new TypeCollector());
    List<Class<Ref>> refClasses = new ArrayList<>(that.declarations.size());
    for (Class<? extends Nameable> c : that.declarations) {
      Class<Ref> refClass = c.acceptVisitor(this);
      refClasses.add(refClass);
    }
    return new Program<>(refClasses, that.range);
  }

  @Override
  public Class<Ref> visitClassDeclaration(Class<? extends Nameable> that) {
    // fieldsAndVariables in current class
    fieldsAndVariables = new SymbolTable();
    fieldsAndVariables.enterScope();
    List<Field<Ref>> newFields = new ArrayList<>(that.fields.size());
    for (Field<? extends Nameable> f : that.fields) {
      if (fieldsAndVariables.inCurrentScope(f.name())) {
        throw new SemanticError();
      }
      fieldsAndVariables.insert(f.name(), f);
      Field<Ref> field = f.acceptVisitor(this);
      newFields.add(field);
    }

    // methods in current class
    methods = new SymbolTable();
    methods.enterScope();
    List<Method<Ref>> newMethods = new ArrayList<>(that.methods.size());
    for (Method<? extends Nameable> m : that.methods) {
      if (methods.inCurrentScope(m.name())) {
        throw new SemanticError();
      }
      methods.insert(m.name(), m);
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
    return new Type<>(new Ref(optDef.get()), that.dimension, that.range);
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

    // go from field scope to method scope
    fieldsAndVariables.enterScope();

    // check for parameters with same name
    for (Parameter<Ref> p : newParams) {
      if (fieldsAndVariables.inCurrentScope(p.name())) {
        throw new SemanticError();
      }
      fieldsAndVariables.insert(p.name(), p);
    }
    Block<Ref> block = (Block<Ref>) that.body.acceptVisitor(this);
    // go back to field scope
    fieldsAndVariables.leaveScope();
    return new Method<>(that.isStatic, returnType, that.name(), newParams, block, that.range());
  }

  @Override
  public Block<Ref> visitBlock(Block<? extends Nameable> that) {
    fieldsAndVariables.enterScope();
    List<BlockStatement<Ref>> newStatements = new ArrayList<>(that.statements.size());
    for (BlockStatement<? extends Nameable> s : that.statements) {
      newStatements.add(s.acceptVisitor(this));
    }
    fieldsAndVariables.leaveScope();
    return new Block<>(newStatements, that.range);
  }

  @Override
  public Statement<Ref> visitEmpty(Statement.Empty<? extends Nameable> that) {
    return new Statement.Empty<>(that.range);
  }

  @Override
  public Statement<Ref> visitIf(Statement.If<? extends Nameable> that) {
    Expression<Ref> newCondition = that.condition.acceptVisitor(this);

    fieldsAndVariables.enterScope();
    Statement<Ref> newThen = that.then.acceptVisitor(this);
    fieldsAndVariables.leaveScope();

    Statement<Ref> newElse = null;
    if (that.else_.isPresent()) {
      fieldsAndVariables.enterScope();
      newElse = that.else_.get().acceptVisitor(this);
      fieldsAndVariables.leaveScope();
    }
    return new Statement.If<>(newCondition, newThen, newElse, that.range);
  }

  @Override
  public Statement<Ref> visitExpressionStatement(
      Statement.ExpressionStatement<? extends Nameable> that) {
    Expression<Ref> expr = that.expression.acceptVisitor(this);
    return new Statement.ExpressionStatement<>(expr, that.range);
  }

  @Override
  public Statement<Ref> visitWhile(Statement.While<? extends Nameable> that) {
    Expression<Ref> cond = that.condition.acceptVisitor(this);
    fieldsAndVariables.enterScope();
    Statement<Ref> body = that.body.acceptVisitor(this);
    fieldsAndVariables.leaveScope();
    return new Statement.While<>(cond, body, that.range);
  }

  @Override
  public Statement<Ref> visitReturn(Statement.Return<? extends Nameable> that) {
    if (that.expression.isPresent()) {
      Expression<Ref> expr = that.expression.get().acceptVisitor(this);
      return new Statement.Return<>(expr, that.range);
    }
    return new Statement.Return<>(null, that.range);
  }

  @Override
  public BlockStatement.Variable<Ref> visitVariable(
      BlockStatement.Variable<? extends Nameable> that) {
    fieldsAndVariables.insert(that.name(), that);
    Type<Ref> variableType = that.type.acceptVisitor(this);
    Expression<Ref> rhsExpression = that.rhs.acceptVisitor(this);
    return new BlockStatement.Variable<>(variableType, that.name(), rhsExpression, that.range());
  }

  @Override
  public Expression<Ref> visitBinaryOperator(Expression.BinaryOperator<? extends Nameable> that) {
    Expression<Ref> left = that.left.acceptVisitor(this);
    Expression<Ref> right = that.right.acceptVisitor(this);
    return new Expression.BinaryOperator<>(that.op, left, right, that.range);
  }

  @Override
  public Expression<Ref> visitUnaryOperator(Expression.UnaryOperator<? extends Nameable> that) {
    Expression<Ref> expr = that.expression.acceptVisitor(this);
    return new Expression.UnaryOperator<>(that.op, expr, that.range);
  }

  @Override
  public Expression<Ref> visitMethodCall(Expression.MethodCall<? extends Nameable> that) {
    Optional<Definition> method = methods.lookup(that.method.name());
    if (method.isPresent()) {
      Expression<Ref> self = that.self.acceptVisitor(this);
      List<Expression<Ref>> newArgs = new ArrayList<>(that.arguments.size());
      for (Expression<? extends Nameable> argument : that.arguments) {
        newArgs.add(argument.acceptVisitor(this));
      }
      return new Expression.MethodCall<>(self, new Ref(method.get()), newArgs, that.range);
    }
    throw new SemanticError("method doesn't exist");
  }

  @Override
  public Expression<Ref> visitFieldAccess(Expression.FieldAccess<? extends Nameable> that) {
    Optional<Definition> field = fieldsAndVariables.lookup(that.field.name());
    if (field.isPresent()) {
      Expression<Ref> self = that.self.acceptVisitor(this);
      return new Expression.FieldAccess<>(self, new Ref(field.get()), that.range);
    }
    throw new SemanticError("Field doesn't exist");
  }

  @Override
  public Expression<Ref> visitArrayAccess(Expression.ArrayAccess<? extends Nameable> that) {
    Expression<Ref> refExpression = that.array.acceptVisitor(this);
    Expression<Ref> idx = that.index.acceptVisitor(this);
    return null;
  }

  @Override
  public Expression<Ref> visitNewObject(Expression.NewObject<? extends Nameable> that) {
    Optional<Definition> optDef = fieldsAndVariables.lookup(that.type.name());
    if (optDef.isPresent()) {
      return new Expression.NewObject<>(new Ref(optDef.get()), that.range);
    }
    throw new SemanticError();
  }

  @Override
  public Expression<Ref> visitNewArray(Expression.NewArray<? extends Nameable> that) {
    Type<Ref> type = that.type.acceptVisitor(this);
    Expression<Ref> expr = that.size.acceptVisitor(this);
    return new Expression.NewArray<>(type, expr, that.range);
  }

  @Override
  public Expression<Ref> visitVariable(Expression.Variable<? extends Nameable> that) {
    Optional<Definition> optDef = fieldsAndVariables.lookup(that.var.name());
    if (optDef.isPresent()) {
      return new Expression.Variable<>(new Ref(optDef.get()), that.range);
    }
    throw new SemanticError();
  }

  @Override
  public Expression<Ref> visitBooleanLiteral(Expression.BooleanLiteral<? extends Nameable> that) {
    return (Expression<Ref>) that;
  }

  @Override
  public Expression<Ref> visitIntegerLiteral(Expression.IntegerLiteral<? extends Nameable> that) {
    return (Expression<Ref>) that;
  }
}
