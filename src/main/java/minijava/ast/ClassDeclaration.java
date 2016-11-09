package minijava.ast;

import java.util.List;
import minijava.ast.classmember.ClassMember;
import minijava.ast.visitors.ClassDeclarationVisitor;

public class ClassDeclaration<TRef> {
  public final String name;
  public final List<ClassMember<TRef>> members;

  public ClassDeclaration(String name, List<ClassMember<TRef>> members) {
    this.name = name;
    this.members = members;
  }

  public <TRet> TRet acceptVisitor(ClassDeclarationVisitor<TRef, TRet> visitor) {
    return visitor.visitClassDeclaration(name, members);
  }
}
