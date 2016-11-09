package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public class IntegerLiteralExpression<TRef> implements Expression<TRef> {

  public final String literal;

  public IntegerLiteralExpression(String literal) {
    this.literal = literal;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitIntegerLiteral(literal);
  }
}
