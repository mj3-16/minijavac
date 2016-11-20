package minijava.util;

import org.jetbrains.annotations.NotNull;

/** Position in the source file. Instances of this class are immutable. */
public class SourcePosition implements Comparable<SourcePosition> {

  public static final SourcePosition BEGIN_OF_PROGRAM = new SourcePosition(0, 1, 0);
  public final int tokenNumber;
  public final int line;
  public final int column;

  public SourcePosition(int tokenNumber, int line, int column) {
    this.tokenNumber = tokenNumber;
    this.line = line;
    this.column = column;
  }

  public SourcePosition moveHorizontal(int length) {
    return new SourcePosition(tokenNumber, line, column + length);
  }

  @Override
  public String toString() {
    return "[" + line + ":" + column + "]";
  }

  @Override
  public int compareTo(@NotNull SourcePosition other) {
    return Integer.compare(tokenNumber, other.tokenNumber);
  }

  // if we implement Comparable, we probably should also implement equals.

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SourcePosition that = (SourcePosition) o;

    return compareTo(that) == 0;
  }

  @Override
  public int hashCode() {
    int result = line;
    result = 31 * result + column;
    return result;
  }
}
