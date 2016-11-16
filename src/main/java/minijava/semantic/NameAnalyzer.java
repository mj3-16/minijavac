package minijava.semantic;

import static minijava.ast.Definition.Kind.*;

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

class NameAnalyzer
    implements Program.Visitor<Nameable, Program<Ref>>,
        Class.Visitor<Nameable, Class<Ref>>,
        Field.Visitor<Nameable, Field<Ref>>,
        Type.Visitor<Nameable, Type<Ref>>,
        Method.Visitor<Nameable, Method<Ref>>,
        BlockStatement.Visitor<Nameable, Statement<Ref>>,
        Expression.Visitor<Nameable, Expression<Ref>> {

  // contains BasicType and Class<? extends Nameable> Definitions
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
    return new Program<>(refClasses, that.getRange());
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
    return new Type<>(new Ref(optDef.get()), that.dimension, that.getRange());
  }

  @Override
  public Method<Ref> visitMethod(Method<? extends Nameable> that) {
    Type<Ref> returnType = that.returnType.acceptVisitor(this);
    // check types and transform parameters
    List<Parameter<Ref>> newParams = new ArrayList<>(that.parameters.size());
    for (Parameter<? extends Nameable> p : that.parameters) {
      Type<Ref> type = p.type.acceptVisitor(this);
      newParams.add(new Parameter<>(type, p.name(), p.getRange()));
    }

    // go from class scope to method scope
    fieldsAndVariables.enterScope();

    // check for parameters with same name
    for (Parameter<Ref> p : newParams) {
      if (fieldsAndVariables.inCurrentScope(p.name())) {
        throw new SemanticError();
      }
      fieldsAndVariables.insert(p.name(), p);
    }
    Block<Ref> block = (Block<Ref>) that.body.acceptVisitor(this);
    // go back to class scope
    fieldsAndVariables.leaveScope();
    return new Method<>(that.isStatic, returnType, that.name(), newParams, block, that.getRange());
  }

  @Override
  public Block<Ref> visitBlock(Block<? extends Nameable> that) {
    fieldsAndVariables.enterScope();
    List<BlockStatement<Ref>> newStatements = new ArrayList<>(that.statements.size());
    for (BlockStatement<? extends Nameable> s : that.statements) {
      newStatements.add(s.acceptVisitor(this));
    }
    fieldsAndVariables.leaveScope();
    return new Block<>(newStatements, that.getRange());
  }

  @Override
  public Statement<Ref> visitEmpty(Statement.Empty<? extends Nameable> that) {
    return new Statement.Empty<>(that.getRange());
  }

  @Override
  public Statement<Ref> visitIf(Statement.If<? extends Nameable> that) {
    Expression<Ref> newCondition = that.condition.acceptVisitor(this);

    fieldsAndVariables.enterScope();
    // then might be a block and therefore enters and leaves another subscope, but that's ok
    Statement<Ref> newThen = that.then.acceptVisitor(this);
    fieldsAndVariables.leaveScope();

    Statement<Ref> newElse = null;
    if (that.else_.isPresent()) {
      fieldsAndVariables.enterScope();
      newElse = that.else_.get().acceptVisitor(this);
      fieldsAndVariables.leaveScope();
    }
    return new Statement.If<>(newCondition, newThen, newElse, that.getRange());
  }

  @Override
  public Statement<Ref> visitExpressionStatement(ExpressionStatement<? extends Nameable> that) {
    Expression<Ref> expr = that.expression.acceptVisitor(this);
    return new ExpressionStatement<>(expr, that.getRange());
  }

  @Override
  public Statement<Ref> visitWhile(Statement.While<? extends Nameable> that) {
    Expression<Ref> cond = that.condition.acceptVisitor(this);
    fieldsAndVariables.enterScope();
    // if body is a block it might enter another subscope (see visitBlock) but that doesn't really matter)
    Statement<Ref> body = that.body.acceptVisitor(this);
    fieldsAndVariables.leaveScope();
    return new Statement.While<>(cond, body, that.getRange());
  }

  @Override
  public Statement<Ref> visitReturn(Statement.Return<? extends Nameable> that) {
    if (that.expression.isPresent()) {
      Expression<Ref> expr = that.expression.get().acceptVisitor(this);
      return new Statement.Return<>(expr, that.getRange());
    }
    return new Statement.Return<>(null, that.getRange());
  }

  @Override
  // TODO: Type problem with BlockStatement.Visitor (needs to return more specific Statement<Ref> because of visitReturn, visitWhile, ...)
  public BlockStatement<Ref> visitVariable(BlockStatement.Variable<? extends Nameable> that) {
    Type<Ref> variableType = that.type.acceptVisitor(this);
    if (fieldsAndVariables.inCurrentScope(that.name())) {
      throw new SemanticError();
    }
    fieldsAndVariables.insert(that.name(), that);
    Expression<Ref> expr = that.rhs.acceptVisitor(this);
    return new BlockStatement.Variable<>(variableType, that.name(), expr, that.getRange());
  }

  @Override
  public Expression<Ref> visitBinaryOperator(Expression.BinaryOperator<? extends Nameable> that) {
    Expression<Ref> left = that.left.acceptVisitor(this);
    Expression<Ref> right = that.right.acceptVisitor(this);
    return new Expression.BinaryOperator<>(that.op, left, right, that.getRange());
  }

  @Override
  public Expression<Ref> visitUnaryOperator(Expression.UnaryOperator<? extends Nameable> that) {
    Expression<Ref> expr = that.expression.acceptVisitor(this);
    return new Expression.UnaryOperator<>(that.op, expr, that.getRange());
  }

  @Override
  public Expression<Ref> visitMethodCall(Expression.MethodCall<? extends Nameable> that) {
    // TODO: how get type of self to look up it method exists?
    throw new SemanticError("method doesn't exist");
  }

  @Override
  public Expression<Ref> visitFieldAccess(Expression.FieldAccess<? extends Nameable> that) {
    // TODO: how do I get the type of self in order to check validity of field access?
    throw new SemanticError("Field doesn't exist");
  }

  @Override
  public Expression<Ref> visitArrayAccess(Expression.ArrayAccess<? extends Nameable> that) {
    // TODO: ((array[3])[2])[2] how to know when we are at the innermost part and how to extract the name so we can look it up?
    Expression<Ref> arr = that.array.acceptVisitor(this);
    Expression<Ref> idx = that.index.acceptVisitor(this);
    return new Expression.ArrayAccess<>(arr, idx, that.getRange());
  }

  @Override
  public Expression<Ref> visitNewObject(NewObject<? extends Nameable> that) {
    Optional<Definition> optDef = types.lookup(that.type.name());
    if (!optDef.isPresent()) {
      throw new SemanticError();
    } else if (optDef.get().kind() == PRIMITIVE_TYPE) {
      throw new SemanticError();
    }
    return new NewObject<>(new Ref(optDef.get()), that.getRange());
  }

  @Override
  public Expression<Ref> visitNewArray(NewArray<? extends Nameable> that) {
    Type<Ref> type = that.type.acceptVisitor(this);
    Expression<Ref> expr = that.size.acceptVisitor(this);
    return new NewArray<>(type, expr, that.getRange());
  }

  @Override
  public Expression<Ref> visitVariable(Expression.Variable<? extends Nameable> that) {
    Optional<Definition> optDef = fieldsAndVariables.lookup(that.var.name());
    if (optDef.isPresent()) {
      assert optDef.get().kind() == FIELD || optDef.get().kind() == VARIABLE;
      return new Expression.Variable<>(new Ref(optDef.get()), that.getRange());
    }
    throw new SemanticError();
  }

  @Override
  public Expression<Ref> visitBooleanLiteral(BooleanLiteral<? extends Nameable> that) {
    // type parameter is not used by BooleanLiteral, cast is safe
    return (BooleanLiteral<Ref>) that;
  }

  @Override
  public Expression<Ref> visitIntegerLiteral(IntegerLiteral<? extends Nameable> that) {
    // type parameter is not used by IntegerLiteral, cast is safe
    return (IntegerLiteral<Ref>) that;
  }
}
