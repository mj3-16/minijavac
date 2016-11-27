package minijava.ast;

/**
 * Collects name resolution information, such as the name of the identifier and its definition after
 * the name has been resolved.
 *
 * @param <T> The type of Definition this points to.
 */
public class Ref<T extends Definition> {

  public final String name;
  public T def;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Ref<?> ref = (Ref<?>) o;

    if (!name.equals(ref.name)) return false;
    return def != null ? def.equals(ref.def) : ref.def == null;
  }

  @Override
  public int hashCode() {
    int result = name.hashCode();
    result = 31 * result + (def != null ? def.hashCode() : 0);
    return result;
  }

  public Ref(T def) {
    this.def = def;
    this.name = def.name();
  }

  public Ref(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }
}
