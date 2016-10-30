package minijava;

import static minijava.token.Terminal.TerminalType.HIDDEN;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.primitives.Booleans;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import minijava.lexer.BasicLexerInput;
import minijava.lexer.Lexer;
import minijava.lexer.SimpleLexer;
import minijava.token.Token;

class Cli {

  static final String usage =
      Joiner.on(System.lineSeparator())
          .join(
              new String[] {
                "Usage: minijavac [--echo|--lextest] [--help] file",
                "",
                "  --echo     write file's content to stdout",
                "  --lextest  run lexical analysis on file's content and print tokens to stdout",
                "  --help     display this help and exit",
                "",
                "  One (and only one) of --echo or --lextest is required."
              });

  private final PrintStream out;
  private final PrintStream err;
  private final FileSystem fileSystem;

  Cli(OutputStream out, OutputStream err, FileSystem fileSystem) {
    this.out = new PrintStream(out);
    this.err = new PrintStream(err);
    this.fileSystem = fileSystem;
  }

  int run(String... args) {
    Parameters params = Parameters.parse(args);
    if (!params.valid()) {
      err.println(usage);
      return 1;
    }
    if (params.help) {
      out.println(usage);
      return 0;
    }
    Path path = fileSystem.getPath(params.file);
    if (!Files.exists(path)) {
      err.println("File '" + params.file + "' doesn't exist!");
      return 1;
    }
    if (params.echo) {
      return echo(path);
    }
    if (params.lextest) {
      return lextest(path);
    }
    // we shouldn't get here
    throw new AssertionError();
  }

  private int echo(Path path) {
    try {
      Files.copy(path, out);
    } catch (IOException e) {
      e.printStackTrace(err);
      return 1;
    }
    return 0;
  }

  private int lextest(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      Lexer lexer = new SimpleLexer(new BasicLexerInput(in));
      lexer.stream().filter(t -> !t.isType(HIDDEN)).map(this::format).forEach(out::println);
      return 0;
    } catch (IOException e) {
      e.printStackTrace(err);
      return 1;
    } catch (MJError e) {
      err.println(e.getMessage());
      return 1;
    }
  }

  private String format(Token t) {
    if (t.isEOF()) {
      return "EOF";
    }
    StringBuilder sb = new StringBuilder();
    switch (t.terminal) {
      case IDENT:
        sb.append("identifier ");
        break;
      case INTEGER_LITERAL:
        sb.append("integer literal ");
        break;
    }
    sb.append(t.lexval);
    return sb.toString();
  }

  private static class Parameters {
    private Parameters() {}

    /** True if the --echo option was set */
    @Parameter(names = "--echo")
    boolean echo;

    /** True if the --lextest option was set */
    @Parameter(names = "--lextest")
    boolean lextest;

    /** True if the --help option was set */
    @Parameter(names = "--help")
    boolean help;

    @Parameter private List<String> mainParameters = new ArrayList<>();

    /** The path of the file to process, possibly relative to the current working directory */
    String file;

    // set to true, if parsing arguments failed
    private boolean invalid;

    /** Returns true if the parameter values represent a valid set */
    boolean valid() {
      return !invalid && (help || ((Booleans.countTrue(echo, lextest) == 1) && (file != null)));
    }

    static Parameters parse(String... args) {
      Parameters params = new Parameters();
      try {
        new JCommander(params, args);
        if (params.mainParameters.size() == 1) {
          params.file = params.mainParameters.get(0);
        }
      } catch (ParameterException e) {
        params.invalid = true;
      }
      return params;
    }
  }
}
