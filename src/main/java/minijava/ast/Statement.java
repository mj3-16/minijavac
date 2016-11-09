package minijava.ast;

import java.util.List;
import java.util.Optional;

public interface Statement<TRef> extends BlockStatement<TRef> {
  <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor);

  default <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return acceptVisitor((StatementVisitor<TRef, TRet>) visitor);
  }

  class Block<TRef> implements Statement<TRef> {

    public final List<BlockStatement<TRef>> statements;

    public Block(List<BlockStatement<TRef>> statements) {
      this.statements = statements;
    }

    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitBlock(this);
    }
  }

  class EmptyStatement<TRef> implements Statement<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitEmptyStatement(this);
    }
  }

  class IfStatement<TRef> implements Statement<TRef> {
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
      return visitor.visitIfStatement(this);
    }
  }

  class ReturnStatement<TRef> implements Statement<TRef> {
    public final Optional<Expression<TRef>> expression;

    public ReturnStatement() {
      this.expression = Optional.empty();
    }

    public ReturnStatement(Expression<TRef> expression) {
      this.expression = Optional.of(expression);
    }

    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitReturnStatement(this);
    }
  }

  class WhileStatement<TRef> implements Statement<TRef> {
    public final Expression<TRef> condition;
    public final Statement<TRef> body;

    public WhileStatement(Expression<TRef> condition, Statement<TRef> body) {
      this.condition = condition;
      this.body = body;
    }

    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitWhileStatement(this);
    }
  }

  class ExpressionStatement<TRef> implements Statement<TRef> {

    public final Expression<TRef> expression;

    public ExpressionStatement(Expression<TRef> expression) {
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitExpressionStatement(this);
    }
  }

  interface StatementVisitor<TRef, TReturn> {

    TReturn visitBlock(Block<TRef> that);

    TReturn visitEmptyStatement(EmptyStatement<TRef> that);

    TReturn visitIfStatement(IfStatement<TRef> that);

    TReturn visitExpressionStatement(ExpressionStatement<TRef> that);

    TReturn visitWhileStatement(WhileStatement<TRef> that);

    TReturn visitReturnStatement(ReturnStatement<TRef> that);
  }
}
