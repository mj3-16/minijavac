package minijava.util;

import static com.google.common.base.Preconditions.checkNotNull;

public interface SyntaxElement {

  SourceRange getRange();

  class DefaultImpl implements SyntaxElement {

    private final SourceRange range;

    public DefaultImpl(SourceRange range) {
      this.range = checkNotNull(range);
    }

    @Override
    public SourceRange getRange() {
      return range;
    }
  }
}
