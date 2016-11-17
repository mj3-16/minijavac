package minijava.ast;

import minijava.util.SourceRange;

public class BasicType implements Definition {
  // TODO: make it an enum (change Nameable.nam(), is final in enum)
  public static final BasicType INT = new BasicType("int");
  public static final BasicType BOOLEAN = new BasicType("boolean");
  public static final BasicType VOID = new BasicType("void");

  private final String name;

  private BasicType(String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Kind kind() {
    return Kind.PRIMITIVE_TYPE;
  }

  @Override
  public SourceRange range() {
    // TODO: Hmm, we regularly use range(), this is no option.
    //throw new UnsupportedOperationException("Basic types are not defined in source code");

    // Let's return a bull shit range instead
    return SourceRange.FIRST_CHAR;
  }
}
