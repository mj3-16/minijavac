package minijava.ast;

import java.util.Optional;

public interface Statement<TRef> extends BlockStatement<TRef> {

  <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor);

  default <TRet> TRet acceptVisitor(BlockStatement.Visitor<? super TRef, TRet> visitor) {
    return acceptVisitor((Statement.Visitor<? super TRef, TRet>) visitor);
  }

  class EmptyStatement<TRef> implements Statement<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor) {
      return visitor.visitEmptyStatement(this);
    }
  }

  class If<TRef> implements Statement<TRef> {
    public final Expression<TRef> condition;
    public final Statement<TRef> then;
    public final Optional<Statement<TRef>> else_;

    public If(Expression<TRef> condition, Statement<TRef> then, Statement<TRef> else_) {
      this.condition = condition;
      this.then = then;
      this.else_ = Optional.ofNullable(else_);
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor) {
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
    public <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor) {
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
    public <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor) {
      return visitor.visitWhile(this);
    }
  }

  class ExpressionStatement<TRef> implements Statement<TRef> {

    public final Expression<TRef> expression;

    public ExpressionStatement(Expression<TRef> expression) {
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor) {
      return visitor.visitExpressionStatement(this);
    }
  }

  interface Visitor<TRef, TRet> {

    TRet visitBlock(Block<? extends TRef> that);

    TRet visitEmptyStatement(EmptyStatement<? extends TRef> that);

    TRet visitIf(If<? extends TRef> that);

    TRet visitExpressionStatement(ExpressionStatement<? extends TRef> that);

    TRet visitWhile(While<? extends TRef> that);

    TRet visitReturn(Return<? extends TRef> that);
  }
}
