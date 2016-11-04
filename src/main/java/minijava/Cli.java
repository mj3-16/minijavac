package minijava;

import static org.jooq.lambda.Seq.seq;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Booleans;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import minijava.lexer.BasicLexerInput;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import minijava.token.Token;

class Cli {

  static final String usage =
      Joiner.on(System.lineSeparator())
          .join(
              new String[] {
                "Usage: minijavac [--echo|--lextest|--parsetest] [--help] file",
                "",
                "  --echo       write file's content to stdout",
                "  --lextest    run lexical analysis on file's content and print tokens to stdout",
                "  --parsetest  run syntacital analysis on file's content",
                "  --help       display this help and exit",
                "",
                "  One (and only one) of --echo, --lextest or --parsetest is required."
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
    try (InputStream in = Files.newInputStream(path)) {
      if (params.echo) {
        echo(in);
      } else if (params.lextest) {
        lextest(in);
      } else if (params.parsetest) {
        parsetest(in);
      }
    } catch (AccessDeniedException e) {
      err.println("error: access to file '" + path + "' was denied");
      return 1;
    } catch (MJError e) {
      err.println(e.getMessage());
      return 1;
    } catch (NoSuchFileException e) {
      err.println("error: file '" + path + "' doesn't exist");
      return 1;
    } catch (Throwable t) {
      // print full stacktrace for any other error
      // if a better description becomes necessary,
      // add a another more specific catch block
      t.printStackTrace(err);
      return 1;
    }
    return 0;
  }

  private void echo(InputStream in) throws IOException {
    ByteStreams.copy(in, out);
  }

  private void lextest(InputStream in) {
    Lexer lexer = new Lexer(new BasicLexerInput(in));
    seq(lexer).map(this::format).forEach(out::println);
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

  private void parsetest(InputStream in) {
    Lexer lexer = new Lexer(new BasicLexerInput(in));
    new Parser(lexer).parse();
  }

  private static class Parameters {
    private Parameters() {}

    /** True if the --echo option was set */
    @Parameter(names = "--echo")
    boolean echo;

    /** True if the --lextest option was set */
    @Parameter(names = "--lextest")
    boolean lextest;

    /** True if the --parsetest option was set */
    @Parameter(names = "--parsetest")
    boolean parsetest;

    /** True if the --help option was set */
    @Parameter(names = "--help")
    boolean help;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @Parameter
    private List<String> mainParameters = new ArrayList<>();

    /** The path of the file to process, possibly relative to the current working directory */
    String file;

    // set to true, if parsing arguments failed
    private boolean invalid;

    /** Returns true if the parameter values represent a valid set */
    boolean valid() {
      return !invalid
          && (help || ((Booleans.countTrue(echo, lextest, parsetest) == 1) && (file != null)));
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
