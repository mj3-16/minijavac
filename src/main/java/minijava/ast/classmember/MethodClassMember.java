package minijava.ast.classmember;

import java.util.List;
import minijava.ast.statement.Block;
import minijava.ast.type.Type;
import minijava.ast.visitors.ClassMemberVisitor;

public class MethodClassMember<TRef> implements ClassMember<TRef> {
  public final boolean isStatic;
  public final Type<TRef> returnType;
  public final String name;
  public final List<Parameter<TRef>> parameters;
  public final Block body;

  public MethodClassMember(
      boolean isStatic,
      Type<TRef> returnType,
      String name,
      List<Parameter<TRef>> parameters,
      Block body) {
    this.isStatic = isStatic;
    this.returnType = returnType;
    this.name = name;
    this.parameters = parameters;
    this.body = body;
  }

  @Override
  public <TRet> TRet acceptVisitor(ClassMemberVisitor<TRef, TRet> visitor) {
    return visitor.visitMethod(isStatic, returnType, name, parameters, body);
  }
}
