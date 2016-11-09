package minijava.ast;

import java.util.List;

public class Class<TRef> {
  public final String name;
  public final List<Member<TRef>> members;

  public Class(String name, List<Member<TRef>> members) {
    this.name = name;
    this.members = members;
  }

  public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitClassDeclaration(this);
  }

  public interface Visitor<TRef, TReturn> {

    TReturn visitClassDeclaration(Class<TRef> that);
  }
}
