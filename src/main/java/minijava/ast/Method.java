package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Method<TRef> extends SyntaxElement.DefaultImpl implements Definition {
  public final boolean isStatic;
  public final Type<TRef> returnType;
  private final String name;
  public final List<Parameter<TRef>> parameters;
  public final Block<TRef> body;
  public Type<Ref> definingClass;

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
      Block<TRef> body,
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
      Type<TRef> returnType,
      String name,
      List<Parameter<TRef>> parameters,
      Block<TRef> body,
      SourceRange range,
      Type<Ref> definingClass) {
    this(isStatic, returnType, name, parameters, body, range);
    this.definingClass = definingClass;
  }

  @Override
  public String name() {
    return this.name;
  }

  public <TRet> TRet acceptVisitor(Visitor<? super TRef, TRet> visitor) {
    return visitor.visitMethod(this);
  }

  public interface Visitor<TRef, TRet> {
    TRet visitMethod(Method<? extends TRef> that);
  }

  public static class Parameter<TRef> extends SyntaxElement.DefaultImpl implements Definition {
    public final Type<TRef> type;
    public final String name;

    public Parameter(Type<TRef> type, String name, SourceRange range) {
      super(range);
      this.type = type;
      this.name = name;
    }

    @Override
    public String name() {
      return name;
    }
  }
}
