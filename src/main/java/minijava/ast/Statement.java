package minijava.ast;

import java.util.Optional;
import minijava.util.SourceRange;
import org.jetbrains.annotations.Nullable;

/*
 * The inheritance relationship between Statement and BlockStatement can be
 * a bit confusing sometimes, so here are a few words of explanation:
 *
 * The fact that Statement extends BlockStatement does _not_ mean that
 * a Statement must always be inside a block. In fact, a block is a valid
 * Statement itself.
 * What this means is that a Statement is always also a valid BlockStatement.
 * It _can_ be used inside a block (as a BlockStatement), but it doesn't have
 * to be used inside a block.
 */
public interface Statement extends BlockStatement {

  <T> T acceptVisitor(Statement.Visitor<T> visitor);

  default <T> T acceptVisitor(BlockStatement.Visitor<T> visitor) {
    // delegate to the acceptVisitor method above, which concrete Statements do implement
    // cast is safe: BlockStatement.Visitor extends Statement.Visitor
    return acceptVisitor((Statement.Visitor<T>) visitor);
  }

  class Empty extends Node implements Statement {
    public Empty(SourceRange range) {
      super(range);
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitEmpty(this);
    }
  }

  class If extends Node implements Statement {
    public Expression condition;
    public final Statement then;
    public final Optional<Statement> else_;

    public If(Expression condition, Statement then, @Nullable Statement else_, SourceRange range) {
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

  class Return extends Node implements Statement {
    public Optional<Expression> expression;

    public Return(@Nullable Expression expression, SourceRange range) {
      super(range);
      this.expression = Optional.ofNullable(expression);
    }

    @Override
    public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
      return visitor.visitReturn(this);
    }
  }

  class While extends Node implements Statement {
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

  class ExpressionStatement extends Node implements Statement {

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
