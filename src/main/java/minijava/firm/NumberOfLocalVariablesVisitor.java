package minijava.firm;

import minijava.ast.Block;
import minijava.ast.Expression;
import minijava.ast.Statement;

/** Created by parttimenerd on 23.11.16. */
public class NumberOfLocalVariablesVisitor
    implements Block.Visitor<Integer>, Expression.Visitor<Integer> {
  @Override
  public Integer visitBlock(Block that) {
    return that.acceptVisitor(this);
  }

  @Override
  public Integer visitEmpty(Statement.Empty that) {
    return 0;
  }

  @Override
  public Integer visitIf(Statement.If that) {
    int elseNum = 0;
    if (that.else_.isPresent()) {
      elseNum = that.else_.get().acceptVisitor(this);
    }
    return Math.max(
        Math.max(that.condition.acceptVisitor(this), that.then.acceptVisitor(this)), elseNum);
  }

  @Override
  public Integer visitExpressionStatement(Statement.ExpressionStatement that) {
    return that.expression.acceptVisitor(this);
  }

  @Override
  public Integer visitWhile(Statement.While that) {
    return Math.max(that.condition.acceptVisitor(this), that.body.acceptVisitor(this));
  }

  @Override
  public Integer visitReturn(Statement.Return that) {
    if (that.expression.isPresent()) {
      return that.expression.get().acceptVisitor(this);
    }
    return 0;
  }

  @Override
  public Integer visitBinaryOperator(Expression.BinaryOperator that) {
    return Math.max(that.left.acceptVisitor(this), that.right.acceptVisitor(this));
  }

  @Override
  public Integer visitUnaryOperator(Expression.UnaryOperator that) {
    return that.expression.acceptVisitor(this);
  }

  @Override
  public Integer visitMethodCall(Expression.MethodCall that) {
    return Math.max(
        1,
        that.arguments
            .stream()
            .map(e -> e.acceptVisitor(this))
            .max(Integer::compare)
            .orElseGet(() -> 0));
  }

  @Override
  public Integer visitFieldAccess(Expression.FieldAccess that) {
    return Math.max(1, that.self.acceptVisitor(this));
  }

  @Override
  public Integer visitArrayAccess(Expression.ArrayAccess that) {
    return Math.max(1, Math.max(that.index.acceptVisitor(this), that.array.acceptVisitor(this)));
  }

  @Override
  public Integer visitNewObject(Expression.NewObject that) {
    return 1;
  }

  @Override
  public Integer visitNewArray(Expression.NewArray that) {
    return Math.max(1, that.size.acceptVisitor(this));
  }

  @Override
  public Integer visitVariable(Expression.Variable that) {
    return 0;
  }

  @Override
  public Integer visitBooleanLiteral(Expression.BooleanLiteral that) {
    return 0;
  }

  @Override
  public Integer visitIntegerLiteral(Expression.IntegerLiteral that) {
    return 0;
  }

  @Override
  public Integer visitReferenceTypeLiteral(Expression.ReferenceTypeLiteral that) {
    return 0;
  }
}
