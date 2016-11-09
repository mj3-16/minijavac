package minijava.ast.type;

import minijava.ast.visitors.TypeVisitor;

public interface Type<TRef> {
  <TRet> TRet acceptVisitor(TypeVisitor<TRef, TRet> visitor);
}
