package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Field<TRef> extends SyntaxElement.DefaultImpl {
  public final Type<TRef> type;
  public final String name;

  public Field(Type<TRef> type, String name, SourceRange range) {
    super(range);
    this.type = type;
    this.name = name;
  }

  public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitField(this);
  }

  public interface Visitor<TRef, TRet> {
    TRet visitField(Field<TRef> that);
  }
}
