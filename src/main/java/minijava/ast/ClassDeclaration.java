package minijava.ast;

import java.util.List;
import minijava.ast.classmember.ClassMember;

public class ClassDeclaration<TRef> {
  public final String name;
  public final List<ClassMember<TRef>> members;

  public ClassDeclaration(String name, List<ClassMember<TRef>> members) {
    this.name = name;
    this.members = members;
  }
}
