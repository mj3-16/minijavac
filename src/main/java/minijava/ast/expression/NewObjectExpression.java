package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class NewObjectExpression<TRef> implements Expression<TRef> {

  public final TRef type;

  public NewObjectExpression(TRef type) {
    this.type = type;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitNewObjectExpr(type);
  }
}
