package minijava.ast;

import minijava.utils.SourceRange;

public class Field extends Node implements Definition {
  public final Type type;
  private final String name;
  public Ref<Class> definingClass;

  public Field(Type type, String name, SourceRange range) {
    super(range);
    this.type = type;
    this.name = name;
  }

  @Override
  public String name() {
    return this.name;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitField(this);
  }

  @Override
  public <T> T acceptVisitor(Definition.Visitor<T> visitor) {
    return visitor.visitField(this);
  }

  public interface Visitor<T> {
    T visitField(Field that);
  }
}
