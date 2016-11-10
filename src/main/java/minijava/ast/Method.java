package minijava.ast;

import java.util.List;

public class Method<TRef> {
  public final boolean isStatic;
  public final Type<TRef> returnType;
  public final String name;
  public final List<Parameter<TRef>> parameters;
  public final Block body;

  /**
   * Constructs a new method node.
   *
   * <p><strong>We do <em>not</em> make a defensive copy</strong> of {@code parameters}. The caller
   * must make sure that, after handing over this list, no modifications happen to it.
   */
  public Method(
      boolean isStatic,
      Type<TRef> returnType,
      String name,
      List<Parameter<TRef>> parameters,
      Block body) {
    this.isStatic = isStatic;
    this.returnType = returnType;
    this.name = name;
    this.parameters = parameters;
    this.body = body;
  }

  public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitMethod(this);
  }

  public interface Visitor<TRef, TRet> {
    TRet visitMethod(Method<TRef> that);
  }

  public static class Parameter<TRef> {
    public final Type<TRef> type;
    public final String name;

    public Parameter(Type<TRef> type, String name) {
      this.type = type;
      this.name = name;
    }
  }
}
