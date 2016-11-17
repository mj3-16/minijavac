package minijava.ast;

import minijava.util.SyntaxElement;

public interface Definition extends Nameable, SyntaxElement {
  Kind kind();

  enum Kind {
    CLASS,
    FIELD,
    METHOD,
    PARAMETER,
    PRIMITIVE_TYPE,
    VARIABLE
  }
}
