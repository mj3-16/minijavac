package minijava.util;

import static com.google.common.base.Preconditions.checkNotNull;

public interface SyntaxElement {

  SourceRange range();

  class DefaultImpl implements SyntaxElement {

    private final SourceRange range;

    public DefaultImpl(SourceRange range) {
      this.range = checkNotNull(range);
    }

    @Override
    public SourceRange range() {
      return range;
    }
  }
}
