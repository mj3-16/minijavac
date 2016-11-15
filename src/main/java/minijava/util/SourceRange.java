package minijava.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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
      int digits = (int) Math.ceil(Math.log10(end.line)) + 1;
      // recall that SourceRange indexes lines starting with 1
      int begin = Math.max(this.begin.line - 1, 1);
      int end = Math.min(this.end.line + 1, sourceFile.size());
      for (int i = begin; i <= end; ++i) {
        sb.append(String.format("%" + digits + "d|> %s", i, sourceFile.get(i - 1)));
        sb.append(System.lineSeparator());
      }
    } else {
      assert begin.line == end.line;
      String prefix = String.format("%d| ", begin.line);
      int squiggleOffset = prefix.length() + begin.column;
      int squiggleLength = end.column - begin.column;
      sb.append(prefix);
      sb.append(sourceFile.get(begin.line - 1));
      sb.append(System.lineSeparator());
      sb.append(Strings.repeat(" ", squiggleOffset));
      sb.append(Strings.repeat("^", squiggleLength));
    }
    return sb.toString();
  }

  public String extractFromSourceString(String source) {
    int line = 1;
    int column = 0;
    StringBuilder sb = new StringBuilder();
    for (char c : Lists.charactersOf(source)) {
      SourcePosition pos = new SourcePosition(line, column);
      if (pos.compareTo(begin) >= 0 && pos.compareTo(end) < 0) {
        sb.append(c);
      } else if (pos.compareTo(end) >= 0) {
        break;
      }

      if (c == '\n') {
        line++;
        column = 0;
      } else {
        column++;
      }
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("%s-%s", begin, end);
  }
}
