package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class BinaryOperatorExpression<TRef> implements Expression<TRef> {
  public final BinOp op;
  public final Expression<TRef> left;
  public final Expression<TRef> right;

  public BinaryOperatorExpression(BinOp op, Expression<TRef> left, Expression<TRef> right) {
    this.op = op;
    this.left = left;
    this.right = right;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitBinaryOperator(op, left, right);
  }
}
