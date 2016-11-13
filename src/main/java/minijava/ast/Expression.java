package minijava.ast;

import java.util.List;

public interface Expression<TRef> {
  <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor);

  class ArrayAccessExpression<TRef> implements Expression<TRef> {

    public final Expression<TRef> array;
    public final Expression<TRef> index;

    public ArrayAccessExpression(Expression<TRef> array, Expression<TRef> index) {
      this.array = array;
      this.index = index;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitArrayAccess(this);
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
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitBinaryOperator(this);
    }
  }

  class BooleanLiteralExpression<TRef> implements Expression<TRef> {

    public final boolean literal;

    public BooleanLiteralExpression(boolean literal) {
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitBooleanLiteral(this);
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
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitFieldAccess(this);
    }
  }

  class IntegerLiteralExpression<TRef> implements Expression<TRef> {

    public final String literal;

    public IntegerLiteralExpression(String literal) {
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitIntegerLiteral(this);
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
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitMethodCall(this);
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
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitNewArrayExpr(this);
    }
  }

  class NewObjectExpression<TRef> implements Expression<TRef> {

    public final TRef type;

    public NewObjectExpression(TRef type) {
      this.type = type;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitNewObjectExpr(this);
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
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitUnaryOperator(this);
    }
  }

  /** Subsumes @null@, @this@ and regular variables. */
  class VariableExpression<TRef> implements Expression<TRef> {

    public final TRef var;

    public VariableExpression(TRef var) {
      this.var = var;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitVariable(this);
    }
  }

  enum UnOp {
    NOT("!"),
    NEGATE("-");

    public final String string;

    UnOp(String string) {
      this.string = string;
    }
  }

  enum BinOp {
    ASSIGN("="),
    PLUS("+"),
    MINUS("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    MODULO("%"),
    OR("||"),
    AND("&&"),
    EQ("=="),
    NEQ("!="),
    LT("<"),
    LEQ("<="),
    GT(">"),
    GEQ(">=");

    public final String string;

    BinOp(String string) {
      this.string = string;
    }
  }

  interface Visitor<TRef, TReturn> {

    TReturn visitBinaryOperator(BinaryOperatorExpression<? extends TRef> that);

    TReturn visitUnaryOperator(UnaryOperatorExpression<? extends TRef> that);

    TReturn visitMethodCall(MethodCallExpression<? extends TRef> that);

    TReturn visitFieldAccess(FieldAccessExpression<? extends TRef> that);

    TReturn visitArrayAccess(ArrayAccessExpression<? extends TRef> that);

    TReturn visitNewObjectExpr(NewObjectExpression<? extends TRef> that);

    TReturn visitNewArrayExpr(NewArrayExpression<? extends TRef> size);

    TReturn visitVariable(VariableExpression<? extends TRef> that);

    TReturn visitBooleanLiteral(BooleanLiteralExpression<? extends TRef> that);

    TReturn visitIntegerLiteral(IntegerLiteralExpression<? extends TRef> that);
  }
}
