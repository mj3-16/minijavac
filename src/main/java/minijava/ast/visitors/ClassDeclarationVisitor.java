package minijava.ast.visitors;

import java.util.List;
import minijava.ast.classmember.ClassMember;

public interface ClassDeclarationVisitor<TRef, TReturn> {

  TReturn visitClassDeclaration(String name, List<ClassMember<TRef>> members);
}
