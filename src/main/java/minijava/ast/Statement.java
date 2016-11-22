package minijava.ast;

import java.util.Optional;
import minijava.util.SourceRange;

public interface Statement extends BlockStatement {

  <T> T acceptVisitor(Statement.Visitor<T> visitor);

  default <T> T acceptVisitor(BlockStatement.Visitor<T> visitor) {
    return acceptVisitor((Statement.Visitor<T>) visitor);
  }

  /** We can't reuse SyntaxElement.DefaultImpl, so this bull shit is necessary */
  abstract class Base implements Statement {
    public final SourceRange range;

    Base(SourceRange range) {
      this.range = range;
    }

    @Override
    public SourceRange range() {
      return range;
    }
  }

  class Empty extends Base {
    public Empty(SourceRange range) {
      super(range);
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitEmpty(this);
    }
  }

  class If extends Base {
    public Expression condition;
    public final Statement then;
    public final Optional<Statement> else_;

    public If(Expression condition, Statement then, Statement else_, SourceRange range) {
      super(range);
      this.condition = condition;
      this.then = then;
      this.else_ = Optional.ofNullable(else_);
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitIf(this);
    }
  }

  class Return extends Base {
    public Optional<Expression> expression;

    public Return(Expression expression, SourceRange range) {
      super(range);
      this.expression = Optional.ofNullable(expression);
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitReturn(this);
    }
  }

  class While extends Base {
    public Expression condition;
    public final Statement body;

    public While(Expression condition, Statement body, SourceRange range) {
      super(range);
      this.condition = condition;
      this.body = body;
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitWhile(this);
    }
  }

  class ExpressionStatement extends Base {

    public Expression expression;

    public ExpressionStatement(Expression expression, SourceRange range) {
      super(range);
      this.expression = expression;
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitExpressionStatement(this);
    }
  }

  interface Visitor<T> {

    T visitBlock(Block that);

    T visitEmpty(Empty that);

    T visitIf(If that);

    T visitExpressionStatement(ExpressionStatement that);

    T visitWhile(While that);

    T visitReturn(Return that);
  }
}
