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
