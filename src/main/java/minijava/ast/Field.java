package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Field<TRef> extends SyntaxElement.DefaultImpl implements Definition {
  public final Type<TRef> type;
  private final String name;
  public Type<Ref> definingClass;

  public Field(Type<TRef> type, String name, SourceRange range) {
    super(range);
    this.type = type;
    this.name = name;
  }

  public Field(Type<TRef> type, String name, SourceRange range, Type<Ref> definingClass) {
    this(type, name, range);
    this.definingClass = definingClass;
  }

  @Override
  public String name() {
    return this.name;
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitField(this);
  }

  public interface Visitor<TRef, TRet> {
    TRet visitField(Field<? extends TRef> that);
  }
}
