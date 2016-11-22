package minijava.ast;

import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class LocalVariable extends SyntaxElement.DefaultImpl implements Definition {
  public final Type type;
  public final String name;

  public LocalVariable(Type type, String name, SourceRange range) {
    super(range);
    this.type = type;
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitLocalVariable(this);
  }
}
