package minijava.ast;

import minijava.util.SourceRange;

public interface Definition extends Nameable {
  Kind kind();

  SourceRange getRange();

  enum Kind {
    CLASS,
    FIELD,
    METHOD,
    PARAMETER,
    PRIMITIVE_TYPE,
    VARIABLE
  }
}
