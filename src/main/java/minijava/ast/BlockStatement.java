package minijava.ast;

public interface BlockStatement<TRef> {
  <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor);

  class Variable<TRef> implements BlockStatement<TRef> {
    public final Type<TRef> type;
    public final String name;
    public final Expression<TRef> rhs;

    public Variable(Type<TRef> type, String name, Expression<TRef> rhs) {
      this.type = type;
      this.name = name;
      this.rhs = rhs;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
      return visitor.visitVariable(this);
    }
  }

  interface Visitor<TRef, TRet> extends Statement.StatementVisitor<TRef, TRet> {

    TRet visitVariable(Variable<TRef> that);
  }
}
