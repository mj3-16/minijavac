package minijava.ast;

public interface BasicType extends Definition {
  <T> T acceptVisitor(Visitor<T> visitor);

  @Override
  default <T> T acceptVisitor(Definition.Visitor<T> visitor) {
    return acceptVisitor((Visitor<T>) visitor);
  }

  // Will contain methods such as size()
  // TODO: Maybe define a visitor
  interface Visitor<T> {
    T visitVoid(BuiltinType that);

    T visitInt(BuiltinType that);

    T visitBoolean(BuiltinType that);

    T visitAny(BuiltinType that);

    T visitClass(Class that);
  }
}
