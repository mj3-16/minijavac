package minijava.ast;

import java.util.List;

public interface Expression<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

    class ArrayAccessExpression<TRef> implements Expression<TRef> {

      public final Expression<TRef> array;
      public final Expression<TRef> index;

      public ArrayAccessExpression(Expression<TRef> array, Expression<TRef> index) {
        this.array = array;
        this.index = index;
      }

      @Override
      public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
        return visitor.visitArrayAccess(array, index);
      }
    }

  class BinaryOperatorExpression<TRef> implements Expression<TRef> {
    public final BinOp op;
    public final Expression<TRef> left;
    public final Expression<TRef> right;

    public BinaryOperatorExpression(BinOp op, Expression<TRef> left, Expression<TRef> right) {
      this.op = op;
      this.left = left;
      this.right = right;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBinaryOperator(op, left, right);
    }
  }

  class BooleanLiteralExpression<TRef> implements Expression<TRef> {

    public final boolean literal;

    public BooleanLiteralExpression(boolean literal) {
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBooleanLiteral(literal);
    }
  }

  class FieldAccessExpression<TRef> implements Expression<TRef> {

    public final Expression<TRef> self;
    public final TRef field;

    public FieldAccessExpression(Expression<TRef> self, TRef field) {
      this.self = self;
      this.field = field;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitFieldAccess(self, field);
    }
  }

  class IntegerLiteralExpression<TRef> implements Expression<TRef> {

    public final String literal;

    public IntegerLiteralExpression(String literal) {
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitIntegerLiteral(literal);
    }
  }

  class MethodCallExpression<TRef> implements Expression<TRef> {

    public final Expression<TRef> self;
    public final TRef method;
    public final List<Expression<TRef>> arguments;

    public MethodCallExpression(
        Expression<TRef> self, TRef method, List<Expression<TRef>> arguments) {
      this.self = self;
      this.method = method;
      this.arguments = arguments;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitMethodCall(self, method, arguments);
    }
  }

  class NewArrayExpression<TRef> implements Expression<TRef> {

    public final Type<TRef> type;
    public final Expression<TRef> size;

    public NewArrayExpression(Type<TRef> type, Expression<TRef> size) {
      this.type = type;
      this.size = size;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitNewArrayExpr(type, size);
    }
  }

  class NewObjectExpression<TRef> implements Expression<TRef> {

    public final TRef type;

    public NewObjectExpression(TRef type) {
      this.type = type;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitNewObjectExpr(type);
    }
  }

  class UnaryOperatorExpression<TRef> implements Expression<TRef> {

    public final UnOp op;
    public final Expression<TRef> expression;

    public UnaryOperatorExpression(UnOp op, Expression<TRef> expression) {
      this.op = op;
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitUnaryOperator(op, expression);
    }
  }

  /** Subsumes @null@, @this@ and regular variables. */
  class VariableExpression<TRef> implements Expression<TRef> {

    public final TRef var;

    public VariableExpression(TRef var) {
      this.var = var;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitVariable(var);
    }
  }

  enum UnOp {
    NOT,
    NEGATE,
  }

  enum BinOp {
    ASSIGN,
    PLUS,
    MINUS,
    MULTIPLY,
    DIVIDE,
    MODULO,
    OR,
    AND,
    EQ,
    NEQ,
    LT,
    LEQ,
    GT,
    GEQ
  }

  interface Visitor<TRef, TReturn> {

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
}
