package minijava.ast;

import minijava.util.SourceRange;

/**
 * Think of this as an enum. We can't make it an enum however, because Java's enum defines a
 * custom @name()@ method and we can't provide a custom implementation just for the interface like
 * it is possible in e.g. C# (which just makes me hate Java even more).
 */
public class BuiltinType implements Definition {
  public static final BuiltinType INT = new BuiltinType("int");
  public static final BuiltinType BOOLEAN = new BuiltinType("boolean");
  public static final BuiltinType VOID = new BuiltinType("void");
  public static final BuiltinType ANY = new BuiltinType("any");

  private final String name;

  private BuiltinType(String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public SourceRange range() {
    // TODO: Hmm, we frequently use range(), this is no option.
    //throw new UnsupportedOperationException("Basic types are not defined in source code");

    // Let's return a bull shit range instead
    return SourceRange.FIRST_CHAR;
  }
}
