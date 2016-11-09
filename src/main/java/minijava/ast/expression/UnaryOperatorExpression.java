package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class UnaryOperatorExpression<TRef> implements Expression<TRef> {

  public final UnOp op;
  public final Expression<TRef> expression;

  public UnaryOperatorExpression(UnOp op, Expression<TRef> expression) {
    this.op = op;
    this.expression = expression;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitUnaryOperator(op, expression);
  }
}
