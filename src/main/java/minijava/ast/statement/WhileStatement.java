package minijava.ast.statement;

import minijava.ast.expression.Expression;
import minijava.ast.visitors.StatementVisitor;

public class WhileStatement<TRef> implements Statement<TRef> {
  public final Expression<TRef> condition;
  public final Statement<TRef> body;

  public WhileStatement(Expression<TRef> condition, Statement<TRef> body) {
    this.condition = condition;
    this.body = body;
  }

  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitWhileStatement(condition, body);
  }
}
