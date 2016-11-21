package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Field extends SyntaxElement.DefaultImpl implements Definition {
  public final Type type;
  private final String name;
  public Ref<Class> definingClass;

  public Field(Type type, String name, SourceRange range) {
    super(range);
    this.type = type;
    this.name = name;
  }

  public Field(Type type, String name, SourceRange range, Ref<Class> definingClass) {
    this(type, name, range);
    this.definingClass = definingClass;
  }

  @Override
  public String name() {
    return this.name;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitField(this);
  }

  public interface Visitor<T> {
    T visitField(Field that);
  }
}
