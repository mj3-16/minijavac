package minijava.ast;

import java.util.List;

public class ClassDeclaration<TRef> {
  public final String name;
  public final List<ClassMember<TRef>> members;

  public ClassDeclaration(String name, List<ClassMember<TRef>> members) {
    this.name = name;
    this.members = members;
  }

  public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitClassDeclaration(name, members);
  }

  public interface Visitor<TRef, TReturn> {

    TReturn visitClassDeclaration(String name, List<ClassMember<TRef>> members);
  }
}
