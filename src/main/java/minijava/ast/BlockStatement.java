package minijava.ast;

import java.util.Optional;
import minijava.util.SourceCodeReferable;
import minijava.util.SourceRange;

public interface BlockStatement extends SourceCodeReferable {
  <T> T acceptVisitor(Visitor<T> visitor);

  class Variable extends LocalVariable implements BlockStatement {
    public Optional<Expression> rhs;

    public Variable(Type type, String name, Expression rhs, SourceRange range) {
      super(type, name, range);
      this.rhs = Optional.ofNullable(rhs);
    }

    @Override
    public <T> T acceptVisitor(BlockStatement.Visitor<T> visitor) {
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
