package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public interface Expression<TRef> extends SyntaxElement {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  /** We can't reuse SyntaxElement.DefaultImpl, so this bull shit is necessary */
  abstract class Base<TRef> implements Expression<TRef> {
    public final SourceRange range;

    Base(SourceRange range) {
      this.range = range;
    }

    @Override
    public SourceRange getRange() {
      return range;
    }
  }

  class ArrayAccess<TRef> extends Base<TRef> {

    public final Expression<TRef> array;
    public final Expression<TRef> index;

    public ArrayAccess(Expression<TRef> array, Expression<TRef> index, SourceRange range) {
      super(range);
      this.array = array;
      this.index = index;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitArrayAccess(this);
    }
  }

  class BinaryOperator<TRef> extends Base<TRef> {
    public final BinOp op;
    public final Expression<TRef> left;
    public final Expression<TRef> right;

    public BinaryOperator(
        BinOp op, Expression<TRef> left, Expression<TRef> right, SourceRange range) {
      super(range);
      this.op = op;
      this.left = left;
      this.right = right;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBinaryOperator(this);
    }
  }

  class BooleanLiteral<TRef> extends Base<TRef> {

    public final boolean literal;

    public BooleanLiteral(boolean literal, SourceRange range) {
      super(range);
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitBooleanLiteral(this);
    }
  }

  class FieldAccess<TRef> extends Base<TRef> {

    public final Expression<TRef> self;
    public final TRef field;

    public FieldAccess(Expression<TRef> self, TRef field, SourceRange range) {
      super(range);
      this.self = self;
      this.field = field;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitFieldAccess(this);
    }
  }

  class IntegerLiteral<TRef> extends Base<TRef> {

    public final String literal;

    public IntegerLiteral(String literal, SourceRange range) {
      super(range);
      this.literal = literal;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitIntegerLiteral(this);
    }
  }

  class MethodCall<TRef> extends Base<TRef> {

    public final Expression<TRef> self;
    public final TRef method;
    public final List<Expression<TRef>> arguments;

    public MethodCall(
        Expression<TRef> self, TRef method, List<Expression<TRef>> arguments, SourceRange range) {
      super(range);
      this.self = self;
      this.method = method;
      this.arguments = arguments;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitMethodCall(this);
    }
  }

  class NewArray<TRef> extends Base<TRef> {

    public final Type<TRef> type;
    public final Expression<TRef> size;

    public NewArray(Type<TRef> type, Expression<TRef> size, SourceRange range) {
      super(range);
      this.type = type;
      this.size = size;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitNewArrayExpr(this);
    }
  }

  class NewObject<TRef> extends Base<TRef> {

    public final TRef type;

    public NewObject(TRef type, SourceRange range) {
      super(range);
      this.type = type;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitNewObjectExpr(this);
    }
  }

  class UnaryOperator<TRef> extends Base<TRef> {

    public final UnOp op;
    public final Expression<TRef> expression;

    public UnaryOperator(UnOp op, Expression<TRef> expression, SourceRange range) {
      super(range);
      this.op = op;
      this.expression = expression;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitUnaryOperator(this);
    }
  }

  /** Subsumes @null@, @this@ and regular variables. */
  class Variable<TRef> extends Base<TRef> {

    public final TRef var;

    public Variable(TRef var, SourceRange range) {
      super(range);
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
