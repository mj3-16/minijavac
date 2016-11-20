package minijava.ast;

import com.google.common.collect.ImmutableList;
import minijava.util.PrettyPrinter;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Type<TRef> extends SyntaxElement.DefaultImpl {

  public static final Type<Ref> INT =
      new Type<>(new Ref(BuiltinType.INT), 0, SourceRange.FIRST_CHAR);
  public static final Type<Ref> BOOLEAN =
      new Type<>(new Ref(BuiltinType.BOOLEAN), 0, SourceRange.FIRST_CHAR);
  public static final Type<Ref> VOID =
      new Type<>(new Ref(BuiltinType.VOID), 0, SourceRange.FIRST_CHAR);
  public static final Type<Ref> ANY =
      new Type<>(new Ref(BuiltinType.ANY), 0, SourceRange.FIRST_CHAR);
  public static final Type<Ref> SYSTEM_OUT = makeSystemOut();

  private static Type<Ref> makeSystemOut() {
    Method<Ref> println =
        new Method<>(
            false,
            VOID,
            "println",
            ImmutableList.of(new Method.Parameter<>(INT, "blub", SourceRange.FIRST_CHAR)),
            null,
            SourceRange.FIRST_CHAR);
    Class<Ref> class_ =
        new Class<>(
            "type of System.out",
            ImmutableList.of(),
            ImmutableList.of(println),
            SourceRange.FIRST_CHAR);
    return new Type<>(new Ref(class_), 0, SourceRange.FIRST_CHAR);
  }

  public final TRef typeRef;
  public final int dimension;

  public Type(TRef typeRef, int dimension, SourceRange range) {
    super(range);
    if (dimension < 0) {
      throw new IndexOutOfBoundsException("dimension was negative");
    }
    this.typeRef = typeRef;
    this.dimension = dimension;
  }

  @Override
  public String toString() {
    try {
      return ((Type<Nameable>) this).acceptVisitor(new PrettyPrinter()).toString();
    } catch (ClassCastException e) {
      return super.toString();
    }
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitType(this);
  }

  public interface Visitor<TRef, TReturn> {
    TReturn visitType(Type<? extends TRef> that);
  }
}
