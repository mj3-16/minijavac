package minijava.lexer;

import java.util.Iterator;

public class TaggedWithLocation<T> {

  public final Location loc;
  public final T tagged;

  public TaggedWithLocation(Location loc, T tagged) {
    this.loc = loc;
    this.tagged = tagged;
  }

  public static Iterator<TaggedWithLocation<Byte>> tagBytes(Iterator<Byte> bytes) {
    return new Iterator<TaggedWithLocation<Byte>>() {

      private int currentLine = 1;
      private int currentColumn = 0;

      @Override
      public boolean hasNext() {
        return bytes.hasNext();
      }

      @Override
      public TaggedWithLocation<Byte> next() {
        Location l = new Location(currentLine, currentColumn);
        byte b = bytes.next();

        if (b == '\n') {
          currentLine++;
          currentColumn = 0;
        } else {
          currentColumn++;
        }

        return new TaggedWithLocation<>(l, b);
      }
    };
  }
}
