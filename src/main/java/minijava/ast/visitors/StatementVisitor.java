package minijava.ast.visitors;

import java.util.List;
import java.util.Optional;
import minijava.ast.expression.Expression;
import minijava.ast.statement.BlockStatement;
import minijava.ast.statement.Statement;

public interface StatementVisitor<TRef, TReturn> {

  TReturn visitBlock(List<BlockStatement<TRef>> block);

  TReturn visitEmptyStatement();

  TReturn visitIfStatement(Expression<TRef> condition, Statement<TRef> then, Statement<TRef> else_);

  TReturn visitExpressionStatement(Expression<TRef> expression);

  TReturn visitWhileStatement(Expression<TRef> condition, Statement<TRef> body);

  TReturn visitReturnStatement(Optional<Expression<TRef>> expression);
}
