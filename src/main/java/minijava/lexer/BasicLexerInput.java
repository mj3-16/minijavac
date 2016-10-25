package minijava.lexer;

import java.io.IOException;
import java.io.InputStream;

import minijava.MJError;

/**
 * A basic lexer input stream without advanced buffering.
 */
public class BasicLexerInput implements LexerInput {

    private final InputStream stream;
    private int currentChar = -2;
    private int currentLine = 1;
    private int currentColumn = 0;

    public BasicLexerInput(InputStream stream) {
        this.stream = stream;
    }

    @Override
    public boolean hasNext() {
        return currentChar != -1;
    }

    @Override
    public Integer next() {
        try {
            currentChar = stream.read();
            if (currentChar == '\n'){
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
    public int current() {
        if (currentChar == -2){
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

    public Location getCurrentLocation(){
        return new Location(currentLine, currentColumn);
    }
}
