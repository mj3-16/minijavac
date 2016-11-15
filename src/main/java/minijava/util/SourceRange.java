package minijava.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A half-open interval in the concrete syntax, denoting the extent of some syntax element. In
 * particular, @end@ is one beyond the last character of the syntax element.
 *
 * <p>This is of course accounted for in error messages.
 */
public class SourceRange {
  public static final SourceRange FIRST_CHAR = new SourceRange(SourcePosition.BEGIN_OF_PROGRAM, 1);
  public final SourcePosition begin;
  public final SourcePosition end; // exclusive!

  public SourceRange(SourcePosition begin, SourcePosition end) {
    this.begin = checkNotNull(begin);
    this.end = checkNotNull(end);
    checkArgument(begin.line <= end.line, "SourceRange ends before it begins");
    // Next line handles invariant begin.line == end.line ===> begin.column < end.column
    checkArgument(
        begin.line != end.line || begin.column < end.column, "SourceRange ends before it begins");
  }

  public SourceRange(SourcePosition begin, int length) {
    this(begin, begin.moveHorizontal(length));
  }

  @Override
  public String toString() {
    return String.format("%s-%s", begin, end);
  }
}
