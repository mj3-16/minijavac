package minijava.ast.type;

import minijava.ast.visitors.TypeVisitor;

public class ArrayType<TRef> implements Type<TRef> {

  public final Type<TRef> elementType;

  public ArrayType(Type<TRef> elementType) {
    this.elementType = elementType;
  }

  @Override
  public <TRet> TRet acceptVisitor(TypeVisitor<TRef, TRet> visitor) {
    return visitor.visitArray(elementType);
  }
}
