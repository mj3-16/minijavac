package minijava.ast;

import java.util.Collections;
import java.util.List;
import minijava.util.SourceRange;
import minijava.util.SyntaxElement;

public class Class<TRef> extends SyntaxElement.DefaultImpl {
  public final String name;
  public final List<Field<TRef>> fields;
  public final List<Method<TRef>> methods;

  /**
   * Constructs a new class node.
   *
   * <p><strong>We do <em>not</em> make defensive copies</strong> of {@code fields} or {@code
   * methods}. The caller must make sure that, after handing over these lists, no modifications
   * happen to them.
   */
  public Class(
      String name, List<Field<TRef>> fields, List<Method<TRef>> methods, SourceRange range) {
    super(range);
    this.name = name;
    this.fields = Collections.unmodifiableList(fields);
    this.methods = Collections.unmodifiableList(methods);
  }

  public <TRet> TRet acceptVisitor(Visitor<TRef, TRet> visitor) {
    return visitor.visitClassDeclaration(this);
  }

  public interface Visitor<TRef, TReturn> {
    TReturn visitClassDeclaration(Class<TRef> that);
  }
}
