package minijava.ast;

import java.util.Collections;
import java.util.List;
import minijava.utils.SourceRange;

public class Class extends Node implements BasicType {
  private final String name;
  public final List<Field> fields;
  public final List<Method> methods;

  /**
   * Constructs a new class node.
   *
   * <p><strong>We do <em>not</em> make defensive copies</strong> of {@code fields} or {@code
   * methods}. The caller must make sure that, after handing over these lists, no modifications
   * happen to them.
   */
  public Class(String name, List<Field> fields, List<Method> methods, SourceRange range) {
    super(range);
    this.name = name;
    this.fields = Collections.unmodifiableList(fields);
    this.methods = Collections.unmodifiableList(methods);
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitClass(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Class aClass = (Class) o;

    if (!name.equals(aClass.name)) return false;
    if (!fields.equals(aClass.fields)) return false;
    return methods.equals(aClass.methods);
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + fields.hashCode();
    result = 31 * result + methods.hashCode();
    return result;
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public <T> T acceptVisitor(BasicType.Visitor<T> visitor) {
    return visitor.visitClass(this);
  }

  public interface Visitor<T> {
    T visitClass(Class that);
  }
}
