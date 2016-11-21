package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Program extends SyntaxElement.DefaultImpl {
  public final List<Class> declarations;

  public Program(List<Class> declarations, SourceRange range) {
    super(range);
    this.declarations = declarations;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitProgram(this);
  }

  public interface Visitor<T> {

    T visitProgram(Program that);
  }
}
