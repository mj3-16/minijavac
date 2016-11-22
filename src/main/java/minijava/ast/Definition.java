package minijava.ast;

import minijava.util.SyntaxElement;

public interface Definition extends Nameable, SyntaxElement {
  <T> T acceptVisitor(Visitor<T> visitor);

  interface Visitor<T> extends BasicType.Visitor<T> {
    T visitField(Field that);

    T visitMethod(Method that);

    T visitLocalVariable(LocalVariable that);
  }
}
