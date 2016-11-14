package minijava.ast;

import java.util.List;

public interface Expression<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  class ArrayAccess<TRef> implements Expression<TRef> {

    public final Expression<TRef> array;
    public final Expression<TRef> index;

    public ArrayAccess(Expression<TRef> array, Expression<TRef> index) {
      this.array = array;
      this.index = index;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitArrayAccess(this);
    }
  }

  class BinaryOperator<TRef> implements Expression<TRef> {
    public final BinOp op;
    public final Expression<TRef> left;
    public final Expression<TRef> right;

    public BinaryOperator(BinOp op, Expression<TRef> left, Expression<TRef> right) {
      this.op = op;
      this.left = left;
      this.right = right;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBinaryOperator(this);
    }
  }

  class BooleanLiteral<TRef> implements Expression<TRef> {

    public final boolean literal;

    public BooleanLiteral(boolean literal) {
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBooleanLiteral(this);
    }
  }

  class FieldAccess<TRef> implements Expression<TRef> {

    public final Expression<TRef> self;
    public final TRef field;

    public FieldAccess(Expression<TRef> self, TRef field) {
      this.self = self;
      this.field = field;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitFieldAccess(this);
    }
  }

  class IntegerLiteral<TRef> implements Expression<TRef> {

    public final String literal;

    public IntegerLiteral(String literal) {
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitIntegerLiteral(this);
    }
  }

  class MethodCall<TRef> implements Expression<TRef> {

    public final Expression<TRef> self;
    public final TRef method;
    public final List<Expression<TRef>> arguments;

    public MethodCall(Expression<TRef> self, TRef method, List<Expression<TRef>> arguments) {
      this.self = self;
      this.method = method;
      this.arguments = arguments;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitMethodCall(this);
    }
  }

  class NewArray<TRef> implements Expression<TRef> {

    public final Type<TRef> type;
    public final Expression<TRef> size;

    public NewArray(Type<TRef> type, Expression<TRef> size) {
      this.type = type;
      this.size = size;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitNewArrayExpr(this);
    }
  }

  class NewObject<TRef> implements Expression<TRef> {

    public final TRef type;

    public NewObject(TRef type) {
      this.type = type;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitNewObjectExpr(this);
    }
  }

  class UnaryOperator<TRef> implements Expression<TRef> {

    public final UnOp op;
    public final Expression<TRef> expression;

    public UnaryOperator(UnOp op, Expression<TRef> expression) {
      this.op = op;
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitUnaryOperator(this);
    }
  }

  /** Subsumes @null@, @this@ and regular variables. */
  class Variable<TRef> implements Expression<TRef> {

    public final TRef var;

    public Variable(TRef var) {
      this.var = var;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
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

    TReturn visitBinaryOperator(BinaryOperator<TRef> that);

    TReturn visitUnaryOperator(UnaryOperator<TRef> that);

    TReturn visitMethodCall(MethodCall<TRef> that);

    TReturn visitFieldAccess(FieldAccess<TRef> that);

    TReturn visitArrayAccess(ArrayAccess<TRef> that);

    TReturn visitNewObjectExpr(NewObject<TRef> that);

    TReturn visitNewArrayExpr(NewArray<TRef> size);

    TReturn visitVariable(Variable<TRef> that);

    TReturn visitBooleanLiteral(BooleanLiteral<TRef> that);

    TReturn visitIntegerLiteral(IntegerLiteral<TRef> that);
  }
}
