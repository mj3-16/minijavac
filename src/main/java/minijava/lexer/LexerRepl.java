package minijava.lexer;

import static org.jooq.lambda.Seq.seq;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import minijava.MJError;
import minijava.token.Token;

/**
 * Read eval print loop for a lexer. Useful to test and debug the lexer.
 *
 * <p>The user can end the REPL by entering an empty line.
 */
public class LexerRepl {

  /**
   * Creates a REPL for a lexer and prints the token types for an entered string.
   *
   * <p>The user can end the REPL by entering an empty line.
   */
  public void run() {
    System.out.println("--- Lexer REPL, exit by entering an empty line");
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
    String line = "";
    try {
      while ((line = input.readLine()) != null && !line.equals("")) {
        Lexer lexer = new Lexer(line);
        try {
          System.out.print(
              "=> " + seq(lexer).map(Token::toString).collect(Collectors.joining(" ")));
        } catch (MJError ex) {
          System.out.print("Caught error: " + ex.getMessage());
        }
        System.out.print("\n");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
