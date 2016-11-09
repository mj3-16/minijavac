package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class FieldAccessExpression<TRef> implements Expression<TRef> {

  public final Expression<TRef> self;
  public final TRef field;

  public FieldAccessExpression(Expression<TRef> self, TRef field) {
    this.self = self;
    this.field = field;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitFieldAccess(self, field);
  }
}
