package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;

public abstract class Expression extends Node {

  /** Type attribute; set in type analysis phase */
  public Type type;

  Expression(SourceRange range) {
    super(range);
  }

  public abstract <T> T acceptVisitor(Visitor<T> visitor);

  public static class ArrayAccess extends Expression {

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

  public static class BinaryOperator extends Expression {
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

  public static class BooleanLiteral extends Expression {

    public final boolean literal;

    public BooleanLiteral(boolean literal, SourceRange range) {
      super(range);
      this.type = Type.BOOLEAN;
      this.literal = literal;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitBooleanLiteral(this);
    }
  }

  public static class FieldAccess extends Expression {

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

  public static class IntegerLiteral extends Expression {

    public final String literal;

    public IntegerLiteral(String literal, SourceRange range) {
      super(range);
      this.type = Type.INT;
      this.literal = literal;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitIntegerLiteral(this);
    }
  }

  public static class MethodCall extends Expression {

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

  public static class NewArray extends Expression {

    public final Type elementType;
    public Expression size;

    public NewArray(Type elementType, Expression size, SourceRange range) {
      super(range);
      this.elementType = elementType;
      this.size = size;
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitNewArray(this);
    }
  }

  public static class NewObject extends Expression {

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

  public static class UnaryOperator extends Expression {

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
  public static class Variable extends Expression {

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

  public static class ReferenceTypeLiteral extends Expression implements Nameable {
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
      ReferenceTypeLiteral ret = new ReferenceTypeLiteral("System.out", range);
      ret.type = Type.SYSTEM_OUT;
      return ret;
    }

    @Override
    public <T> T acceptVisitor(Expression.Visitor<T> visitor) {
      return visitor.visitReferenceTypeLiteral(this);
    }

    @Override
    public String name() {
      return name;
    }
  }

  public enum UnOp {
    NOT("!"),
    NEGATE("-");

    public final String string;

    UnOp(String string) {
      this.string = string;
    }
  }

  public enum BinOp {
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

  public interface Visitor<T> {

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
