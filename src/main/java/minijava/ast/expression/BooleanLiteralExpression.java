package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class BooleanLiteralExpression<TRef> implements Expression<TRef> {

  public final boolean literal;

  public BooleanLiteralExpression(boolean literal) {
    this.literal = literal;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitBooleanLiteral(literal);
  }
}
