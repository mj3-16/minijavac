package minijava.ast;

import minijava.utils.SourceRange;

/**
 * Think of this as an enum. We can't make it an enum however, because Java's enum defines a
 * custom @name()@ method and we can't provide a custom implementation just for the interface like
 * it is possible in e.g. C# (which just makes me hate Java even more).
 */
public class BuiltinType implements BasicType {
  public static final BuiltinType INT = new BuiltinType("int");
  public static final BuiltinType BOOLEAN = new BuiltinType("boolean");
  public static final BuiltinType VOID = new BuiltinType("void");
  public static final BuiltinType ANY_REF = new BuiltinType("any");

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
    // Let's return a bull shit range instead
    return SourceRange.FIRST_CHAR;
  }

  @Override
  public <T> T acceptVisitor(Visitor<T> visitor) {
    switch (name) {
      case "int":
        return visitor.visitInt(this);
      case "boolean":
        return visitor.visitBoolean(this);
      case "void":
        return visitor.visitVoid(this);
      case "any":
        return visitor.visitAny(this);
      default:
        throw new UnsupportedOperationException("Unknown builtin type " + name);
    }
  }
}
