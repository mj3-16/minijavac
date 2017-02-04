package minijava;

import static org.jooq.lambda.Seq.seq;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Booleans;
import firm.Dump;
import firm.Graph;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import minijava.Compiler.Backend;
import minijava.ast.Program;
import minijava.lexer.Lexer;
import minijava.token.Token;
import org.slf4j.event.Level;
import org.slf4j.impl.SimpleLogger;

public class Cli {

  static final String usage =
      Joiner.on(System.lineSeparator())
          .join(
              new String[] {
                "Usage: minijavac [--echo|--lextest|--parsetest|--check|--compile-firm|--print-asm] [--optimize] [--help] [--verbosity] file",
                "",
                "  --echo          write file's content to stdout",
                "  --lextest       run lexical analysis on file's content and print tokens to stdout",
                "  --parsetest     run syntactical analysis on file's content",
                "  --print-ast     pretty-print abstract syntax tree to stdout",
                "  --check         parse the given file and perform semantic checks",
                "  --compile-firm  compile the given file with the libfirm amd64 backend",
                "  --run-firm      compile and run the given file with libfirm amd64 backend",
                "  --print-asm     compile the given file and output the generated assembly",
                "  --optimize|-O   Optimization level of produced IR. 0-3",
                "  --verbosity|-v  Crank this up for more debug output",
                "  --help          display this help and exit",
                "",
                "  If no flag is given, the passed file is compiled to a.out",
                " Set the environment variable MJ_USE_GC to \"1\" to use the bdwgc."
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
    setLogLevel(params.verbosity);
    if (!params.valid()) {
      err.println("Called as: " + String.join(" ", args));
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
      } else if (params.printAst) {
        printAst(in);
      } else if (params.check) {
        check(in);
      } else if (params.compileFirm) {
        compileFirm(in, params.optimizationLevel);
      } else if (params.runFirm) {
        runFirm(in);
      } else if (params.printAsm) {
        printAsm(in, params.optimizationLevel);
      } else {
        compile(in, params.optimizationLevel);
      }
    } catch (AccessDeniedException e) {
      err.println("error: access to file '" + path + "' was denied");
      return 1;
    } catch (MJError e) {
      try {
        err.println("error: " + e.getSourceReferencingMessage(Files.readAllLines(path)));
      } catch (IOException io) {
        err.println(e.getMessage());
      }
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

  private void setLogLevel(int verbosity) {
    verbosity = Math.max(0, verbosity);
    verbosity = Math.min(Level.values().length - 1, verbosity);
    // HACK ALERT
    String level = Level.values()[verbosity].toString();
    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, level);
  }

  private void echo(InputStream in) throws IOException {
    ByteStreams.copy(in, out);
  }

  private void lextest(InputStream in) {
    Lexer lexer = Compiler.lex(in);
    seq(lexer).map(Token::toString).forEach(out::println);
  }

  private void parsetest(InputStream in) {
    Compiler.lexAndParse(in);
  }

  private void printAst(InputStream in) {
    Program ast = Compiler.lexAndParse(in);
    out.print(Compiler.prettyPrint(ast));
  }

  private void check(InputStream in) {
    Program ast = Compiler.lexAndParse(in);
    Compiler.checkSemantics(ast);
    Compiler.verifySemanticAnnotations(ast);
  }

  /** Compiles (with/out optimizations) with the firm backend. */
  private void compileFirm(InputStream in, int optimizationLevel) throws IOException {
    Compiler.produceFirmIR(in, optimizationLevel);
    Compiler.compile(Backend.FIRM, "a.out", shouldProduceDebuggableBinary());
  }

  private static boolean shouldPrintGraphs() {
    String value = System.getenv("MJ_GRAPH");
    return value != null && value.equals("1");
  }

  private boolean shouldProduceDebuggableBinary() {
    String value = System.getenv("MJ_DBG");
    return value != null && value.equals("1");
  }

  public static void dumpGraphsIfNeeded(String appendix) {
    for (Graph g : firm.Program.getGraphs()) {
      dumpGraphIfNeeded(g, appendix);
    }
  }

  public static void dumpGraphIfNeeded(Graph g, String appendix) {
    if (shouldPrintGraphs()) {
      g.check();
      Dump.dumpGraph(g, appendix);
    }
  }

  private void runFirm(InputStream in) throws IOException {
    Compiler.produceFirmIR(in, 0);
    Compiler.compile(Backend.FIRM, "a.out", shouldProduceDebuggableBinary());
    runCompiledProgram("a.out");
  }

  private void runCompiledProgram(String outFile) throws IOException {
    Process p = Runtime.getRuntime().exec("./" + outFile);
    int c;
    while ((c = p.getInputStream().read()) != -1) {
      System.out.print(Character.toString((char) c));
    }
    try {
      p.waitFor();
    } catch (Throwable t) {
    }
  }

  private void printAsm(InputStream in, int optimizationLevel) throws IOException {
    Compiler.produceFirmIR(in, optimizationLevel);
    Compiler.Backend.OWN.printAsm(out, Optional.empty());
  }

  private void compile(InputStream in, int optimizationLevel) throws IOException {
    Compiler.produceFirmIR(in, optimizationLevel);
    Compiler.compile(Backend.OWN, "a.out", shouldProduceDebuggableBinary());
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

    /** True if the --print-ast option was set */
    @Parameter(names = "--print-ast")
    boolean printAst;

    /** True if the --check option was set */
    @Parameter(names = "--check")
    boolean check;

    /** True if the --compile-firm option was set */
    @Parameter(names = "--compile-firm")
    boolean compileFirm;

    /** True if the --run-firm option was set */
    @Parameter(names = "--run-firm")
    boolean runFirm;

    /** True if the --print-asm option was set */
    @Parameter(names = "--print-asm")
    boolean printAsm;

    @Parameter(names = {"--verbosity", "-v"})
    Integer verbosity = 0;

    @Parameter(names = {"--optimize", "-O"})
    Integer optimizationLevel = 3;

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
          && (help
              || ((Booleans.countTrue(
                          echo, lextest, parsetest, printAst, check, compileFirm, runFirm, printAsm)
                      <= 1)
                  && (file != null)
                  && (!echo || mainParameters.size() <= 1)));
    }

    static Parameters parse(String... args) {
      Parameters params = new Parameters();
      try {
        new JCommander(params, args);
        params.file = params.mainParameters.get(params.mainParameters.size() - 1);
      } catch (ParameterException e) {
        params.invalid = true;
      }
      return params;
    }
  }
}
