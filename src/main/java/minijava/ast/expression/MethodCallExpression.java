package minijava.ast.expression;

import java.util.List;
import minijava.ast.visitors.ExpressionVisitor;

public class MethodCallExpression<TRef> implements Expression<TRef> {

  public final Expression<TRef> self;
  public final TRef method;
  public final List<Expression<TRef>> arguments;

  public MethodCallExpression(
      Expression<TRef> self, TRef method, List<Expression<TRef>> arguments) {
    this.self = self;
    this.method = method;
    this.arguments = arguments;
  }

  @Override
  public <TRet> TRet acceptVisitor(ExpressionVisitor<TRef, TRet> visitor) {
    return visitor.visitMethodCall(self, method, arguments);
  }
}
