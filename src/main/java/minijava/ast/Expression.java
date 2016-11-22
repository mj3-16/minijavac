package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public interface Expression extends SyntaxElement {
  <T> T acceptVisitor(Visitor<T> visitor);

  /** We can't reuse SyntaxElement.DefaultImpl, so this bull shit is necessary */
  abstract class Base implements Expression {
    public final SourceRange range;

    Base(SourceRange range) {
      this.range = range;
    }

    @Override
    public SourceRange range() {
      return range;
    }
  }

  class ArrayAccess extends Base {

    public Expression array;
    public Expression index;

    public ArrayAccess(Expression array, Expression index, SourceRange range) {
      super(range);
      this.array = array;
      this.index = index;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitArrayAccess(this);
    }
  }

  class BinaryOperator extends Base {
    public final BinOp op;
    public Expression left;
    public Expression right;

    public BinaryOperator(BinOp op, Expression left, Expression right, SourceRange range) {
      super(range);
      this.op = op;
      this.left = left;
      this.right = right;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitBinaryOperator(this);
    }
  }

  class BooleanLiteral extends Base {

    public final boolean literal;

    public BooleanLiteral(boolean literal, SourceRange range) {
      super(range);
      this.literal = literal;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitBooleanLiteral(this);
    }
  }

  class FieldAccess extends Base {

    public Expression self;
    public final Ref<Field> field;

    public FieldAccess(Expression self, Ref<Field> field, SourceRange range) {
      super(range);
      this.self = self;
      this.field = field;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitFieldAccess(this);
    }
  }

  class IntegerLiteral extends Base {

    public final String literal;

    public IntegerLiteral(String literal, SourceRange range) {
      super(range);
      this.literal = literal;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitIntegerLiteral(this);
    }
  }

  class MethodCall extends Base {

    public Expression self;
    public final Ref<Method> method;
    public final List<Expression> arguments;

    public MethodCall(
        Expression self, Ref<Method> method, List<Expression> arguments, SourceRange range) {
      super(range);
      this.self = self;
      this.method = method;
      this.arguments = arguments;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitMethodCall(this);
    }
  }

  class NewArray extends Base {

    public final Type type;
    public Expression size;

    public NewArray(Type type, Expression size, SourceRange range) {
      super(range);
      this.type = type;
      this.size = size;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitNewArray(this);
    }
  }

  class NewObject extends Base {

    public final Ref<Class> class_;

    public NewObject(Ref<Class> class_, SourceRange range) {
      super(range);
      this.class_ = class_;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitNewObject(this);
    }
  }

  class UnaryOperator extends Base {

    public final UnOp op;
    public Expression expression;

    public UnaryOperator(UnOp op, Expression expression, SourceRange range) {
      super(range);
      this.op = op;
      this.expression = expression;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitUnaryOperator(this);
    }
  }

  /** Subsumes @null@, @this@ and regular variables. */
  class Variable extends Base {

    public final Ref<Definition> var;

    public Variable(Ref<Definition> var, SourceRange range) {
      super(range);
      this.var = var;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitVariable(this);
    }
  }

  class ReferenceTypeLiteral extends Base implements Definition {
    private final String name;

    private ReferenceTypeLiteral(String name, SourceRange range) {
      super(range);
      this.name = name;
    }

    public static ReferenceTypeLiteral this_(SourceRange range) {
      return new ReferenceTypeLiteral("this", range);
    }

    public static ReferenceTypeLiteral null_(SourceRange range) {
      return new ReferenceTypeLiteral("null", range);
    }

    public static ReferenceTypeLiteral systemOut(SourceRange range) {
      return new ReferenceTypeLiteral("System.out", range);
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitReferenceTypeLiteral(this);
    }

    @Override
    public String name() {
      return name;
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

  interface Visitor<T> {

    T visitBinaryOperator(BinaryOperator that);

    T visitUnaryOperator(UnaryOperator that);

    T visitMethodCall(MethodCall that);

    T visitFieldAccess(FieldAccess that);

    T visitArrayAccess(ArrayAccess that);

    T visitNewObject(NewObject that);

    T visitNewArray(NewArray that);

    T visitVariable(Variable that);

    T visitBooleanLiteral(BooleanLiteral that);

    T visitIntegerLiteral(IntegerLiteral that);

    T visitReferenceTypeLiteral(ReferenceTypeLiteral that);
  }
}
