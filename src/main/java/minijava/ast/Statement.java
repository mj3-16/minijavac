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
        return visitor.visitBlock(statements);
      }
    }

  class EmptyStatement<TRef> implements Statement<TRef> {
    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitEmptyStatement();
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
      return visitor.visitIfStatement(condition, then, else_);
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
      return visitor.visitReturnStatement(expression);
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
      return visitor.visitWhileStatement(condition, body);
    }
  }

  class ExpressionStatement<TRef> implements Statement<TRef> {

    public final Expression<TRef> expression;

    public ExpressionStatement(Expression<TRef> expression) {
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
      return visitor.visitExpressionStatement(expression);
    }
  }

  interface StatementVisitor<TRef, TReturn> {

    TReturn visitBlock(List<BlockStatement<TRef>> block);

    TReturn visitEmptyStatement();

    TReturn visitIfStatement(Expression<TRef> condition, Statement<TRef> then, Statement<TRef> else_);

    TReturn visitExpressionStatement(Expression<TRef> expression);

    TReturn visitWhileStatement(Expression<TRef> condition, Statement<TRef> body);

    TReturn visitReturnStatement(Optional<Expression<TRef>> expression);
  }
}
