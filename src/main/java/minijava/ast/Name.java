package minijava.ast;

public class Name implements Nameable {

  private final String name;

  public Name(String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }
}
