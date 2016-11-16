package minijava.ast;

import minijava.util.SourceRange;

public interface Definition extends Nameable {
  //Kind kind();

  SourceRange range();

  enum Kind {
    CLASS,
    PRIMITIVE_TYPE,
    FIELD
  }
}
