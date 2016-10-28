package minijava.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class InputStreamIterator implements Iterator<Byte> {

  private final InputStream input;

  public InputStreamIterator(InputStream input) {
    this.input = input;
  }

  @Override
  public boolean hasNext() {
    try {
      return input.available() > 0;
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public Byte next() {
    try {
      return (byte) input.read();
    } catch (IOException e) {
      throw new NoSuchElementException();
    }
  }
}
