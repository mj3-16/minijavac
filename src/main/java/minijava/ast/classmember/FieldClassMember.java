package minijava.ast.classmember;

import minijava.ast.type.Type;
import minijava.ast.visitors.ClassMemberVisitor;

public class FieldClassMember<TRef> implements ClassMember<TRef> {

  public final Type<TRef> type;
  public final String name;

  public FieldClassMember(Type<TRef> type, String name) {
    this.type = type;
    this.name = name;
  }

  @Override
  public <TRet> TRet acceptVisitor(ClassMemberVisitor<TRef, TRet> visitor) {
    return visitor.visitField(type, name);
  }
}
