package minijava.ast.expression;

import minijava.ast.visitors.ExpressionVisitor;

public interface Expression<TRef> {
  <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor);
}
