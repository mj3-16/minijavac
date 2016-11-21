package minijava.ast;

public class Ref implements Nameable {

  public final String name;
  public Definition def;

  public Ref(Definition def) {
    this.def = def;
    this.name = def.name();
  }

  public Ref(String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }
}
