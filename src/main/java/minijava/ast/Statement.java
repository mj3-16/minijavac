package minijava.ast;

import java.util.Optional;
import minijava.util.SourceRange;

public interface Statement<TRef> extends BlockStatement<TRef> {

  <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor);

  default <TRet> TRet acceptVisitor(BlockStatement.Visitor<TRef, TRet> visitor) {
    return acceptVisitor((Statement.Visitor<TRef, TRet>) visitor);
  }

  /** We can't reuse SyntaxElement.DefaultImpl, so this bull shit is necessary */
  abstract class Base<TRef> implements Statement<TRef> {
    public final SourceRange range;

    Base(SourceRange range) {
      this.range = range;
    }

    @Override
    public SourceRange getRange() {
      return range;
    }
  }

  class Empty<TRef> extends Base<TRef> {
    public Empty(SourceRange range) {
      super(range);
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitEmptyStatement(this);
    }
  }

  class If<TRef> extends Base<TRef> {
    public final Expression<TRef> condition;
    public final Statement<TRef> then;
    public final Optional<Statement<TRef>> else_;

    public If(
        Expression<TRef> condition,
        Statement<TRef> then,
        Statement<TRef> else_,
        SourceRange range) {
      super(range);
      this.condition = condition;
      this.then = then;
      this.else_ = Optional.ofNullable(else_);
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitIf(this);
    }
  }

  class Return<TRef> extends Base<TRef> {
    public final Optional<Expression<TRef>> expression;

    public Return(Expression<TRef> expression, SourceRange range) {
      super(range);
      this.expression = Optional.ofNullable(expression);
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitReturn(this);
    }
  }

  class While<TRef> extends Base<TRef> {
    public final Expression<TRef> condition;
    public final Statement<TRef> body;

    public While(Expression<TRef> condition, Statement<TRef> body, SourceRange range) {
      super(range);
      this.condition = condition;
      this.body = body;
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitWhile(this);
    }
  }

  class ExpressionStatement<TRef> extends Base<TRef> {

    public final Expression<TRef> expression;

    public ExpressionStatement(Expression<TRef> expression, SourceRange range) {
      super(range);
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitExpressionStatement(this);
    }
  }

  interface Visitor<TRef, TRet> {

    TRet visitBlock(Block<TRef> that);

    TRet visitEmptyStatement(Empty<TRef> that);

    TRet visitIf(If<TRef> that);

    TRet visitExpressionStatement(ExpressionStatement<TRef> that);

    TRet visitWhile(While<TRef> that);

    TRet visitReturn(Return<TRef> that);
  }
}
