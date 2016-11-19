package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Field<TRef> extends SyntaxElement.DefaultImpl implements Definition {
  public final Type<TRef> type;
  private final String name;

  public Field(Type<TRef> type, String name, SourceRange range) {
    super(range);
    this.type = type;
    this.name = name;
  }

  @Override
  public String name() {
    return this.name;
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitField(this);
  }

  @Override
  public SourceRange range() {
    return null;
  }

  public interface Visitor<TRef, TRet> {
    TRet visitField(Field<? extends TRef> that);
  }
}
