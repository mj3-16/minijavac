package minijava.ast.statement;

import minijava.ast.expression.Expression;
import minijava.ast.visitors.StatementVisitor;

public class IfStatement<TRef> implements Statement<TRef> {
  public final Expression<TRef> condition;
  public final Statement<TRef> then;
  public final Statement<TRef> else_;

  public IfStatement(Expression<TRef> condition, Statement<TRef> then, Statement<TRef> else_) {
    this.condition = condition;
    this.then = then;
    this.else_ = else_;
  }

  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitIfStatement(condition, then, else_);
  }
}
