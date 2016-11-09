package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class ArrayAccessExpression<TRef> implements Expression<TRef> {

  public final Expression<TRef> array;
  public final Expression<TRef> index;

  public ArrayAccessExpression(Expression<TRef> array, Expression<TRef> index) {
    this.array = array;
    this.index = index;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitArrayAccess(array, index);
  }
}
