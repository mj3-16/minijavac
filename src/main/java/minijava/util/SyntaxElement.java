package minijava.util;

import static com.google.common.base.Preconditions.checkNotNull;

public interface SyntaxElement {

  SourceRange getRange();

  class DefaultImpl {

    public final SourceRange range;

    public DefaultImpl(SourceRange range) {
      this.range = checkNotNull(range);
    }
  }
}
