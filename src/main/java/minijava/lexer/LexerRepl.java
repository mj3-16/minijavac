package minijava.lexer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import minijava.MJError;

/**
 * Read eval print loop for a lexer.
 * Useful to test and debug the lexer.
 *
 * The user can end the REPL by entering an empty line.
 */
public class LexerRepl {

    private Function<LexerInput, Lexer> lexerCreator;


    /**
     * Creates a repl.
     *
     * @param lexerCreator function to create a lexer from an input stream.
     */
    public LexerRepl(Function<LexerInput, Lexer> lexerCreator) {
        this.lexerCreator = lexerCreator;
    }

    /**
     * Creates a REPL for a lexer and prints the token types for an entered string.
     *
     * The user can end the REPL by entering an empty line.
     */
    public void run(){
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        String line = "";
        System.out.println("--- Lexer REPL, exit by entering an empty line");
        try {
            while ((line = input.readLine()) != null && !line.equals("")){
                Lexer lexer = createLexer(line);
                try {
                    System.out.print("=> " + createTokenStream(line)
                            .map(Token::toString).collect(Collectors.joining(" ")));
                } catch (MJError ex){
                    System.out.print("Caught error: " + ex.getMessage());
                }
                System.out.print("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Lexer createLexer(String input){
        return lexerCreator.apply(new BasicLexerInput(new ByteArrayInputStream(input.getBytes())));
    }

    public Stream<Token> createTokenStream(String input){
        final Lexer lexer = createLexer(input);
        final Iterable<Token> iterable = () -> lexer;
        return StreamSupport.stream(iterable.spliterator(), false);
    }
}
