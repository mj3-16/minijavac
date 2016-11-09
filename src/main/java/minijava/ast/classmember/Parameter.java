package minijava.ast.classmember;

import minijava.ast.type.Type;

public class Parameter<TRef> {
  public final Type<TRef> type;
  public final String name;

  public Parameter(Type<TRef> type, String name) {
    this.type = type;
    this.name = name;
  }
}
