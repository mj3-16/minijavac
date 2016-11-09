package minijava.ast;

import java.util.List;

public class Program<TRef> {
  public final List<ClassDeclaration<TRef>> declarations;

  public Program(List<ClassDeclaration<TRef>> declarations) {
    this.declarations = declarations;
  }

  public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitProgram(this);
  }

  public interface Visitor<TRef, TReturn> {

    TReturn visitProgram(Program<TRef> that);
  }
}
