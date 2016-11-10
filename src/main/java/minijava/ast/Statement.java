package minijava.ast;

import java.util.Optional;

public interface Statement<TRef> extends BlockStatement<TRef> {

  <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor);

  default <TRet> TRet acceptVisitor(BlockStatement.Visitor<TRef, TRet> visitor) {
    return acceptVisitor((Statement.Visitor<TRef, TRet>) visitor);
  }

  class EmptyStatement<TRef> implements Statement<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitEmptyStatement(this);
    }
  }

  class If<TRef> implements Statement<TRef> {
    public final Expression<TRef> condition;
    public final Statement<TRef> then;
    public final Statement<TRef> else_;

    public If(Expression<TRef> condition, Statement<TRef> then, Statement<TRef> else_) {
      this.condition = condition;
      this.then = then;
      this.else_ = else_;
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitIf(this);
    }
  }

  class Return<TRef> implements Statement<TRef> {
    public final Optional<Expression<TRef>> expression;

    public Return() {
      this.expression = Optional.empty();
    }

    public Return(Expression<TRef> expression) {
      this.expression = Optional.of(expression);
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitReturn(this);
    }
  }

  class While<TRef> implements Statement<TRef> {
    public final Expression<TRef> condition;
    public final Statement<TRef> body;

    public While(Expression<TRef> condition, Statement<TRef> body) {
      this.condition = condition;
      this.body = body;
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitWhile(this);
    }
  }

  class ExpressionStatement<TRef> implements Statement<TRef> {

    public final Expression<TRef> expression;

    public ExpressionStatement(Expression<TRef> expression) {
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<TRef, TRet> visitor) {
      return visitor.visitExpressionStatement(this);
    }
  }

  interface Visitor<TRef, TRet> {

    TRet visitBlock(Block<TRef> that);

    TRet visitEmptyStatement(EmptyStatement<TRef> that);

    TRet visitIf(If<TRef> that);

    TRet visitExpressionStatement(ExpressionStatement<TRef> that);

    TRet visitWhile(While<TRef> that);

    TRet visitReturn(Return<TRef> that);
  }
}
