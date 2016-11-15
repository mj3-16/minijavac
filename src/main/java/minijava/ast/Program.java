package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Program<TRef> extends SyntaxElement.DefaultImpl {
  public final List<Class<TRef>> declarations;

  public Program(List<Class<TRef>> declarations, SourceRange range) {
    super(range);
    this.declarations = declarations;
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitProgram(this);
  }

  public interface Visitor<TRef, TReturn> {

    TReturn visitProgram(Program<? extends TRef> that);
  }
}
