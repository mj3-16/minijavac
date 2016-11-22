package minijava.semantic;

import minijava.ast.*;
import minijava.ast.Class;

/**
 * Performs checks on the AST.
 *
 * <p>1. Making sure that all `Ref`s have been resolved (e.g. their `def` is non-null).
 */
public class SemanticLinter
    implements Program.Visitor<Void>,
        Class.Visitor<Void>,
        Field.Visitor<Void>,
        Type.Visitor<Void>,
        Method.Visitor<Void>,
        BlockStatement.Visitor<Void>,
        Expression.Visitor<Void>,
        Definition.Visitor<Void> {
  @Override
  public Void visitProgram(Program that) {
    that.declarations.forEach(d -> d.acceptVisitor((Class.Visitor<Void>) this));
    return null;
  }

  @Override
  public Void visitField(Field that) {
    that.type.acceptVisitor(this);
    that.definingClass.def.acceptVisitor((Class.Visitor<Void>) this);
    return null;
  }

  @Override
  public Void visitVariable(BlockStatement.Variable that) {
    that.type.acceptVisitor(this);
    that.rhs.map(r -> r.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitClass(Class that) {
    that.fields.forEach(f -> f.acceptVisitor((Field.Visitor<Void>) this));
    that.methods.forEach(m -> m.acceptVisitor((Method.Visitor<Void>) this));
    return null;
  }

  @Override
  public Void visitMethod(Method that) {
    that.returnType.acceptVisitor(this);
    that.definingClass.def.acceptVisitor((Class.Visitor<Void>) this);
    that.parameters.forEach(p -> p.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitLocalVariable(LocalVariable that) {
    if (that instanceof BlockStatement.Variable) {
      return ((BlockStatement.Variable) that).acceptVisitor((BlockStatement.Visitor<Void>) this);
    }
    that.type.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitType(Type that) {
    that.basicType.def.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitBlock(Block that) {
    that.statements.forEach(s -> s.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitEmpty(Statement.Empty that) {
    return null;
  }

  @Override
  public Void visitIf(Statement.If that) {
    that.condition.acceptVisitor(this);
    that.then.acceptVisitor(this);
    that.else_.map(e -> e.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitExpressionStatement(Statement.ExpressionStatement that) {
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitWhile(Statement.While that) {
    that.condition.acceptVisitor(this);
    that.body.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitReturn(Statement.Return that) {
    that.expression.map(e -> e.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitBinaryOperator(Expression.BinaryOperator that) {
    that.left.acceptVisitor(this);
    that.right.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitUnaryOperator(Expression.UnaryOperator that) {
    that.expression.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitMethodCall(Expression.MethodCall that) {
    that.self.acceptVisitor(this);
    that.method.def.acceptVisitor((Method.Visitor<Void>) this);
    that.arguments.forEach(a -> a.acceptVisitor(this));
    return null;
  }

  @Override
  public Void visitFieldAccess(Expression.FieldAccess that) {
    that.self.acceptVisitor(this);
    that.field.def.acceptVisitor((Field.Visitor<Void>) this);
    return null;
  }

  @Override
  public Void visitArrayAccess(Expression.ArrayAccess that) {
    that.array.acceptVisitor(this);
    that.index.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitNewObject(Expression.NewObject that) {
    that.class_.def.acceptVisitor((Class.Visitor<Void>) this);
    return null;
  }

  @Override
  public Void visitNewArray(Expression.NewArray that) {
    that.type.acceptVisitor(this);
    that.size.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitVariable(Expression.Variable that) {
    that.var.def.acceptVisitor(this);
    return null;
  }

  @Override
  public Void visitBooleanLiteral(Expression.BooleanLiteral that) {
    return null;
  }

  @Override
  public Void visitIntegerLiteral(Expression.IntegerLiteral that) {
    return null;
  }

  @Override
  public Void visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    return null;
  }

  @Override
  public Void visitVoid(BuiltinType that) {
    return null;
  }

  @Override
  public Void visitInt(BuiltinType that) {
    return null;
  }

  @Override
  public Void visitBoolean(BuiltinType that) {
    return null;
  }

  @Override
  public Void visitAny(BuiltinType that) {
    return null;
  }
}
