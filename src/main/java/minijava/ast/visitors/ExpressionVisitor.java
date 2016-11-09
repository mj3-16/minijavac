package minijava.ast.visitors;

import java.util.List;
import minijava.ast.expression.BinOp;
import minijava.ast.expression.Expression;
import minijava.ast.expression.UnOp;
import minijava.ast.type.Type;

public interface ExpressionVisitor<TRef, TReturn> {

  TReturn visitBinaryOperator(BinOp op, Expression<TRef> left, Expression<TRef> right);

  TReturn visitUnaryOperator(UnOp op, Expression<TRef> expression);

  TReturn visitMethodCall(Expression<TRef> self, TRef method, List<Expression<TRef>> arguments);

  TReturn visitFieldAccess(Expression<TRef> self, TRef field);

  TReturn visitArrayAccess(Expression<TRef> array, Expression<TRef> index);

  TReturn visitNewObjectExpr(TRef type);

  TReturn visitNewArrayExpr(Type<TRef> type, Expression<TRef> size);

  TReturn visitVariable(TRef var);

  TReturn visitBooleanLiteral(boolean literal);

  TReturn visitIntegerLiteral(String literal);
}
