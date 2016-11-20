package minijava.semantic;

import minijava.MJError;

public class SemanticError extends MJError {
  SemanticError(String s) {
    super(s);
  }

  SemanticError() {
    super("semantic error");
  }
}
