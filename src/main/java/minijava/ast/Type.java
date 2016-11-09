package minijava.ast;

public class Type<TRef> {

  public final TRef typeRef;
  public final int dimension;

  public Type(TRef typeRef, int dimension) {
    this.typeRef = typeRef;
    this.dimension = dimension;
  }

  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitType(this);
  }

  interface Visitor<TRef, TReturn> {
    TReturn visitType(Type<TRef> that);
  }
}
