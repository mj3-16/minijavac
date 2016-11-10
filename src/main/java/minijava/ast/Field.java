package minijava.ast;

public class Field<TRef> {
  public final Type<TRef> type;
  public final String name;

  public Field(Type<TRef> type, String name) {
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
