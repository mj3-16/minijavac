package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

/** Subsumes @null@, @this@ and regular variables. */
public class VariableExpression<TRef> implements Expression<TRef> {

  public final TRef var;

  public VariableExpression(TRef var) {
    this.var = var;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitVariable(var);
  }
}
