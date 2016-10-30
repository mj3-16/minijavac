package minijava.lexer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import minijava.MJError;
import minijava.token.Position;

/** A basic lexer input stream without advanced buffering. */
public class BasicLexerInput implements LexerInput {

  private final InputStream stream;
  private byte currentChar = -2;
  private int currentLine = 1;
  private int currentColumn = 0;

  public BasicLexerInput(InputStream stream) {
    this.stream = new BufferedInputStream(stream);
  }

  @Override
  public boolean hasNext() {
    return currentChar != -1;
  }

  @Override
  public Byte next() {
    try {
      int c = stream.read();
      if (c > 127) {
        throw new LexerError(
            getCurrentPosition(), String.format("Unsupported character with code %d", c));
      }
      currentChar = (byte) c;
      if (currentChar == '\n') {
        currentColumn = 0;
        currentLine++;
      } else {
        currentColumn++;
      }
    } catch (IOException e) {
      throw new MJError(e);
    }
    return currentChar;
  }

  @Override
  public byte current() {
    if (currentChar == -2) {
      next();
    }
    return currentChar;
  }

  @Override
  public void close() {
    try {
      stream.close();
    } catch (IOException e) {
      throw new MJError(e);
    }
  }

  public Position getCurrentPosition() {
    return new Position(currentLine, currentColumn);
  }
}
