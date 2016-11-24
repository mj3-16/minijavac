package minijava.ast;

import minijava.util.SourceCodeReferable;

public interface Definition extends Nameable, SourceCodeReferable {
  <T> T acceptVisitor(Visitor<T> visitor);

  interface Visitor<T> extends BasicType.Visitor<T> {
    T visitField(Field that);

    T visitMethod(Method that);

    T visitLocalVariable(LocalVariable that);
  }
}
