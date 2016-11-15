package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Type<TRef> extends SyntaxElement.DefaultImpl {

  public final TRef typeRef;
  public final int dimension;

  public Type(TRef typeRef, int dimension, SourceRange range) {
    super(range);
    this.typeRef = typeRef;
    this.dimension = dimension;
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitType(this);
  }

  public interface Visitor<TRef, TReturn> {
    TReturn visitType(Type<? extends TRef> that);
  }
}
