package minijava.ast;

import java.util.List;

public class Program<TRef> {
  public final List<Class<TRef>> declarations;

  public Program(List<Class<TRef>> declarations) {
    this.declarations = declarations;
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitProgram(this);
  }

  public interface Visitor<TRef, TReturn> {

    TReturn visitProgram(Program<? extends TRef> that);
  }
}
