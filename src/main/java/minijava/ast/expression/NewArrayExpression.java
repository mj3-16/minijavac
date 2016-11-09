package minijava.ast.expression;

import minijava.ast.type.Type;
import minijava.ast.visitors.ExpressionVisitor;

public class NewArrayExpression<TRef> implements Expression<TRef> {

  public final Type<TRef> type;
  public final Expression<TRef> size;

  public NewArrayExpression(Type<TRef> type, Expression<TRef> size) {
    this.type = type;
    this.size = size;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitNewArrayExpr(type, size);
  }
}
