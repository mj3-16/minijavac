package minijava.ast.statement;

import java.util.Optional;
import minijava.ast.expression.Expression;
import minijava.ast.visitors.StatementVisitor;

public class ReturnStatement<TRef> implements Statement<TRef> {
  public final Optional<Expression<TRef>> expression;

  public ReturnStatement() {
    this.expression = Optional.empty();
  }

  public ReturnStatement(Expression<TRef> expression) {
    this.expression = Optional.of(expression);
  }

  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitReturnStatement(expression);
  }
}
