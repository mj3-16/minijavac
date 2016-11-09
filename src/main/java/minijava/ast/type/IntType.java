package minijava.ast.type;

import minijava.ast.visitors.TypeVisitor;

public class IntType<TRef> implements Type<TRef> {
  @Override
  public <TRet> TRet acceptVisitor(TypeVisitor<TRef, TRet> visitor) {
    return visitor.visitInt();
  }
}
