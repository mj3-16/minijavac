package minijava.ast.type;

import minijava.ast.visitors.TypeVisitor;

public class ClassType<TRef> implements Type<TRef> {

  public final TRef classRef;

  public ClassType(TRef classRef) {
    this.classRef = classRef;
  }

  @Override
  public <TRet> TRet acceptVisitor(TypeVisitor<TRef, TRet> visitor) {
    return visitor.visitClass(classRef);
  }
}
