package minijava.ast;

import com.google.common.collect.ImmutableList;
import minijava.util.PrettyPrinter;
import minijava.util.SourceRange;

/** A compound type, like int[][]. */
public class Type extends Node {

  public static final Type INT = new Type(new Ref<>(BuiltinType.INT), 0, SourceRange.FIRST_CHAR);
  public static final Type BOOLEAN =
      new Type(new Ref<>(BuiltinType.BOOLEAN), 0, SourceRange.FIRST_CHAR);
  public static final Type VOID = new Type(new Ref<>(BuiltinType.VOID), 0, SourceRange.FIRST_CHAR);
  public static final Type ANY_REF =
      new Type(new Ref<>(BuiltinType.ANY_REF), 0, SourceRange.FIRST_CHAR);
  public static final Type SYSTEM_OUT = makeSystemOut();
  public static final Type SYSTEM_IN = makeSystemIn();

  private static Type makeSystemOut() {
    Method println =
        new Method(
            false,
            true,
            VOID,
            "println",
            ImmutableList.of(new LocalVariable(INT, "blub", SourceRange.FIRST_CHAR)),
            null,
            SourceRange.FIRST_CHAR);
    Method write =
        new Method(
            false,
            true,
            VOID,
            "write",
            ImmutableList.of(new LocalVariable(INT, "blub", SourceRange.FIRST_CHAR)),
            null,
            SourceRange.FIRST_CHAR);
    Method flush =
        new Method(false, true, VOID, "flush", ImmutableList.of(), null, SourceRange.FIRST_CHAR);
    Class class_ =
        new Class(
            "type of System.out",
            ImmutableList.of(),
            ImmutableList.of(println, write, flush),
            SourceRange.FIRST_CHAR);
    return new Type(new Ref<>(class_), 0, SourceRange.FIRST_CHAR);
  }

  private static Type makeSystemIn() {
    Method read =
        new Method(false, true, INT, "read", ImmutableList.of(), null, SourceRange.FIRST_CHAR);
    Class class_ =
        new Class(
            "type of System.in",
            ImmutableList.of(),
            ImmutableList.of(read),
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Type type = (Type) o;

    if (dimension != type.dimension) return false;
    return basicType != null ? basicType.equals(type.basicType) : type.basicType == null;
  }

  @Override
  public int hashCode() {
    int result = basicType != null ? basicType.hashCode() : 0;
    result = 31 * result + dimension;
    return result;
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
