package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public interface BlockStatement<TRef> extends SyntaxElement {
  <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor);

  /** We can't reuse SyntaxElement.DefaultImpl, so this bull shit is necessary */
  abstract class Base<TRef> implements BlockStatement<TRef> {
    public final SourceRange range;

    Base(SourceRange range) {
      this.range = range;
    }

    @Override
    public SourceRange range() {
      return range;
    }
  }

  class Variable<TRef> extends Base<TRef> implements Definition {
    public final Type<TRef> type;
    private final String name;
    public final Expression<TRef> rhs;

    public Variable(Type<TRef> type, String name, Expression<TRef> rhs, SourceRange range) {
      super(range);
      this.type = type;
      this.name = name;
      this.rhs = rhs;
    }

    @Override
    public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
      return visitor.visitVariable(this);
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public Kind kind() {
      return Kind.VARIABLE;
    }
  }

  interface Visitor<TRef, TRet> extends Statement.Visitor<TRef, TRet> {

    TRet visitVariable(Variable<? extends TRef> that);
  }
}
