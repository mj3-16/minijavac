package minijava.ast;

public class Ref implements Nameable {

  private final Definition def;

  public Ref(Definition def) {
    this.def = def;
  }

  @Override
  public String name() {
    return def.name();
  }
}
