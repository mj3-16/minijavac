package minijava.ast;

import java.util.Optional;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public interface BlockStatement extends SyntaxElement {
  <T> T acceptVisitor(Visitor<T> visitor);

  /** We can't reuse SyntaxElement.DefaultImpl, so this bull shit is necessary */
  abstract class Base implements BlockStatement {
    public final SourceRange range;

    Base(SourceRange range) {
      this.range = range;
    }

    @Override
    public SourceRange range() {
      return range;
    }
  }

  class Variable extends Base implements Definition {
    public final Type type;
    private final String name;
    public Optional<Expression> rhs;

    public Variable(Type type, String name, Expression rhs, SourceRange range) {
      super(range);
      this.type = type;
      this.name = name;
      this.rhs = Optional.ofNullable(rhs);
    }

    @Override
    public <T> T acceptVisitor(Visitor<T> visitor) {
      return visitor.visitVariable(this);
    }

    @Override
    public String name() {
      return this.name;
    }
  }

  interface Visitor<T> extends Statement.Visitor<T> {

    T visitVariable(Variable that);
  }
}
