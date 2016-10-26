package minijava;

import static minijava.lexer.Terminal.FALSE;
import static minijava.lexer.Terminal.NULL;
import static minijava.lexer.Terminal.RESERVED_IDENTIFIER;
import static minijava.lexer.Terminal.THIS;
import static minijava.lexer.Terminal.TRUE;
import static minijava.lexer.Terminal.TerminalType.CONTROL_FLOW;
import static minijava.lexer.Terminal.TerminalType.HIDDEN;
import static minijava.lexer.Terminal.TerminalType.OPERATOR;
import static minijava.lexer.Terminal.TerminalType.SYNTAX_ELEMENT;
import static minijava.lexer.Terminal.TerminalType.TYPE;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import minijava.lexer.BasicLexerInput;
import minijava.lexer.Lexer;
import minijava.lexer.SimpleLexer;

class Cli {
  @Parameter(names = "--echo", description = "print given file on stdout")
  private String echoPath;

  @Parameter(
    names = "--lextest",
    description = "lex given file and print the resulting tokens on stdout"
  )
  private String lextestPath;

  @Parameter(names = "--help", help = true, description = "print this usage information")
  private boolean help;

  private final PrintStream out;
  private final PrintStream err;
  private final JCommander jCommander;

  private Cli(OutputStream out, OutputStream err, String... args) {
    this.out = new PrintStream(out);
    this.err = new PrintStream(err);
    this.jCommander = new JCommander(this, args);
  }

  public static Cli create(OutputStream out, OutputStream err, String... args)
      throws InvalidCommandLineArguments {
    try {
      return new Cli(out, err, args);
    } catch (ParameterException ex) {
      throw new InvalidCommandLineArguments(ex.getMessage());
    }
  }

  int run() {
    if (help) {
      out.print(getUsageInfo());
      return 0;
    }
    if (echoPath != null) {
      try {
        InputStream in = Files.newInputStream(Paths.get(echoPath));
        ByteStreams.copy(in, out);
        in.close();
      } catch (IOException e) {
        e.printStackTrace(err);
        return 1;
      }
    }
    if (lextestPath != null) {
      return lexTest();
    }
    return 0;
  }

  private int lexTest() {
    try {
      InputStream in = Files.newInputStream(Paths.get(lextestPath));
      outputLexerTokens(new SimpleLexer(new BasicLexerInput(in)));
      return 0;
    } catch (IOException e) {
      e.printStackTrace();
      return 1;
    } catch (MJError e) {
      err.println(e.getMessage());
      return 1;
    }
  }

  private void outputLexerTokens(Lexer lexer) {
    lexer
        .stream()
        .forEach(
            token -> {
              if (token.isType(HIDDEN)) { // ws or comments
                return;
              }
              if (token.isEOF()) {
                out.println("EOF");
              }
              if (token.isType(OPERATOR)
                  || token.isType(SYNTAX_ELEMENT)
                  || token.isType(TYPE)
                  || token.isTerminal(NULL)
                  || token.isTerminal(TRUE)
                  || token.isTerminal(FALSE)
                  || token.isTerminal(RESERVED_IDENTIFIER)
                  || token.isType(CONTROL_FLOW)
                  || token.isTerminal(THIS)) {
                out.println(token.getContentString());
                return;
              }
              switch (token.getTerminal()) {
                case IDENT:
                  out.println("identifier " + token.getContentString());
                  break;
                case INTEGER_LITERAL:
                  out.println("integer literal " + token.getContentString());
                  break;
              }
            });
  }

  public static String getUsageInfo() {
    StringBuilder sb = new StringBuilder();
    new Cli(System.out, System.err).jCommander.usage(sb);
    return sb.toString();
  }
}
