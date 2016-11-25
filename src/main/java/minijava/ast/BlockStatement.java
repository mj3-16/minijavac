package minijava.ast;

import java.util.Optional;
import minijava.util.SourceCodeReferable;
import minijava.util.SourceRange;
import org.jetbrains.annotations.Nullable;

public interface BlockStatement extends SourceCodeReferable {
  <T> T acceptVisitor(Visitor<T> visitor);

  class Variable extends LocalVariable implements BlockStatement {
    public Optional<Expression> rhs;

    public Variable(Type type, String name, @Nullable Expression rhs, SourceRange range) {
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

  /**
   * A visitor for objects of type {@link BlockStatement}. Since all {@link Statement}s are valid
   * {@link BlockStatement}s, an implementation must also satisfy the {@link Statement.Visitor}
   * interface and therefore this interface extends {@link Statement.Visitor}.
   */
  interface Visitor<T> extends Statement.Visitor<T> {

    T visitVariable(Variable that);
  }
}
