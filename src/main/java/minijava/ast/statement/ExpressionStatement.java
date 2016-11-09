package minijava.ast.statement;

import minijava.ast.expression.Expression;
import minijava.ast.visitors.StatementVisitor;

public class ExpressionStatement<TRef> implements Statement<TRef> {

  public final Expression<TRef> expression;

  public ExpressionStatement(Expression<TRef> expression) {
    this.expression = expression;
  }

  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitExpressionStatement(expression);
  }
}
