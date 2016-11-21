package minijava.ast;

import com.google.common.collect.ImmutableList;
import minijava.util.PrettyPrinter;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

/** A compound type, like int[][]. */
public class Type extends SyntaxElement.DefaultImpl {

  public static final Type INT = new Type(new Ref<>(BuiltinType.INT), 0, SourceRange.FIRST_CHAR);
  public static final Type BOOLEAN =
      new Type(new Ref<>(BuiltinType.BOOLEAN), 0, SourceRange.FIRST_CHAR);
  public static final Type VOID = new Type(new Ref<>(BuiltinType.VOID), 0, SourceRange.FIRST_CHAR);
  public static final Type ANY_REF =
      new Type(new Ref<>(BuiltinType.ANY_REF), 0, SourceRange.FIRST_CHAR);
  public static final Type SYSTEM_OUT = makeSystemOut();

  private static Type makeSystemOut() {
    Method println =
        new Method(
            false,
            VOID,
            "println",
            ImmutableList.of(new Method.Parameter(INT, "blub", SourceRange.FIRST_CHAR)),
            null,
            SourceRange.FIRST_CHAR);
    Class class_ =
        new Class(
            "type of System.out",
            ImmutableList.of(),
            ImmutableList.of(println),
            SourceRange.FIRST_CHAR);
    return new Type(new Ref<>(class_), 0, SourceRange.FIRST_CHAR);
  }

  public final Ref<BasicType> basicType;
  public final int dimension;

  public Type(Ref<BasicType> basicType, int dimension, SourceRange range) {
    super(range);
    if (dimension < 0) {
      throw new IndexOutOfBoundsException("dimension was negative");
    }
    this.basicType = basicType;
    this.dimension = dimension;
  }

  @Override
  public String toString() {
    return this.acceptVisitor(new PrettyPrinter()).toString();
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitType(this);
  }

  public interface Visitor<T> {
    T visitType(Type that);
  }
}
