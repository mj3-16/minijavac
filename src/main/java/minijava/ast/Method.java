package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Method extends SyntaxElement.DefaultImpl implements Definition {
  public final boolean isStatic;
  public final Type returnType;
  private final String name;
  public final List<LocalVariable> parameters;
  public final Block body;
  public Ref<Class> definingClass;

  /**
   * Constructs a new method node.
   *
   * <p><strong>We do <em>not</em> make a defensive copy</strong> of {@code parameters}. The caller
   * must make sure that, after handing over this list, no modifications happen to it.
   */
  public Method(
      boolean isStatic,
      Type returnType,
      String name,
      List<LocalVariable> parameters,
      Block body,
      SourceRange range) {
    super(range);
    this.isStatic = isStatic;
    this.returnType = returnType;
    this.name = name;
    this.parameters = parameters;
    this.body = body;
  }

  public Method(
      boolean isStatic,
      Type returnType,
      String name,
      List<LocalVariable> parameters,
      Block body,
      SourceRange range,
      Ref<Class> definingClass) {
    this(isStatic, returnType, name, parameters, body, range);
    this.definingClass = definingClass;
  }

  @Override
  public String name() {
    return this.name;
  }

  public <TRet> TRet acceptVisitor(Visitor<TRet> visitor) {
    return visitor.visitMethod(this);
  }

  @Override
  public <T> T acceptVisitor(Definition.Visitor<T> visitor) {
    return visitor.visitMethod(this);
  }

  public interface Visitor<TRet> {
    TRet visitMethod(Method that);
  }
}
