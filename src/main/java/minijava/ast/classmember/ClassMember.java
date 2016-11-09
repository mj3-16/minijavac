package minijava.ast.classmember;

import minijava.ast.visitors.ClassMemberVisitor;

public interface ClassMember<TRef> {
  <TRet> TRet acceptVisitor(ClassMemberVisitor<TRef, TRet> visitor);
}
