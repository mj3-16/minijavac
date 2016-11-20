package minijava.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import java.util.List;

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

  public String annotateSourceFileExcerpt(List<String> sourceFile) {
    StringBuilder sb = new StringBuilder();
    if (begin.line < end.line) {
      // we can only really squiggle at the side
      int digits = (int) Math.floor(Math.log10(end.line)) + 1;
      // recall that SourceRange indexes lines starting with 1
      int begin = Math.max(this.begin.line, 1);
      int end = Math.min(this.end.line, sourceFile.size());
      for (int i = begin; i <= end; ++i) {
        sb.append(String.format("%" + digits + "d|> %s", i, sourceFile.get(i - 1)));
        sb.append(System.lineSeparator());
      }
    } else {
      assert begin.line == end.line;

      int line0 = begin.line - 1; // recall that lines start with 1
      if (line0 >= sourceFile.size()) {
        // squiggle the EOF
        line0 = sourceFile.size() - 1;
        String prefix = String.format("%d| ", line0 + 1);
        sb.append(prefix);
        String lastLine = sourceFile.get(line0);
        sb.append(lastLine);
        sb.append(System.lineSeparator());
        sb.append(Strings.repeat(" ", prefix.length() + lastLine.length()));
        sb.append("^");
        sb.append(System.lineSeparator());
      } else {
        String prefix = String.format("%d| ", line0 + 1);
        sb.append(prefix);
        int squiggleOffset = prefix.length() + begin.column;
        int squiggleLength = end.column - begin.column;
        sb.append(sourceFile.get(line0));
        sb.append(System.lineSeparator());
        sb.append(Strings.repeat(" ", squiggleOffset));
        sb.append(Strings.repeat("^", squiggleLength));
        sb.append(System.lineSeparator());
      }
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("%s-%s", begin, end);
  }
}
