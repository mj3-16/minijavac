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
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import minijava.ast.Program;
import minijava.ir.IREmitter;
import minijava.ir.assembler.SimpleNodeAllocator;
import minijava.ir.assembler.block.AssemblerFile;
import minijava.ir.optimize.*;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import minijava.semantic.SemanticAnalyzer;
import minijava.semantic.SemanticLinter;
import minijava.token.Token;
import minijava.util.PrettyPrinter;

class Cli {

  static final String usage =
      Joiner.on(System.lineSeparator())
          .join(
              new String[] {
                "Usage: minijavac [--echo|--lextest|--parsetest|--check|--compile-firm|--print-asm] [--help] file",
                "",
                "  --echo          write file's content to stdout",
                "  --lextest       run lexical analysis on file's content and print tokens to stdout",
                "  --parsetest     run syntactical analysis on file's content",
                "  --print-ast     pretty-print abstract syntax tree to stdout",
                "  --check         parse the given file and perform semantic checks",
                "  --compile-firm  compile the given file with the libfirm amd64 backend",
                "  --run-firm      compile and run the given file with libfirm amd64 backend",
                "  --print-asm     compile the given file and output the generated assembly",
                "  --help          display this help and exit",
                "",
                "  One (and only one) of the passed flags is required.",
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
      } else if (params.printAst) {
        printAst(in);
      } else if (params.check) {
        check(in);
      } else if (params.compileFirm) {
        compileFirm(in);
      } else if (params.runFirm) {
        runFirm(in);
      } else if (params.printAsm) {
        printAsm(in);
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

  private void echo(InputStream in) throws IOException {
    ByteStreams.copy(in, out);
  }

  private void lextest(InputStream in) {
    Lexer lexer = new Lexer(in);
    seq(lexer).map(Token::toString).forEach(out::println);
  }

  private void parsetest(InputStream in) {
    Lexer lexer = new Lexer(in);
    new Parser(lexer).parse();
  }

  private void printAst(InputStream in) {
    Program ast = new Parser(new Lexer(in)).parse();
    out.print(ast.acceptVisitor(new PrettyPrinter()));
  }

  private void check(InputStream in) {
    Program ast = new Parser(new Lexer(in)).parse();
    ast.acceptVisitor(new SemanticAnalyzer());
    ast.acceptVisitor(new SemanticLinter());
  }

  private void compileFirm(InputStream in) throws IOException {
    Program ast = new Parser(new Lexer(in)).parse();
    ast.acceptVisitor(new SemanticAnalyzer());
    ast.acceptVisitor(new SemanticLinter());
    ast.acceptVisitor(new IREmitter());
    if (!optimizationsTurnedOff()) {
      optimize();
    }
    IREmitter.compile("a.out");
  }

  private boolean optimizationsTurnedOff() {
    String value = System.getenv("MJ_OPTIMIZE");
    return value != null && value.equals("0");
  }

  private void optimize() {
    Optimizer constantFolder = new ConstantFolder();
    Optimizer controlFlowOptimizer = new ConstantControlFlowOptimizer();
    Optimizer unreachableCodeRemover = new UnreachableCodeRemover();
    Optimizer algebraicSimplifier = new AlgebraicSimplifier();
    Optimizer phiOptimizer = new PhiOptimizer();
    Optimizer phiBElimination = new PhiBElimination();
    for (Graph graph : firm.Program.getGraphs()) {
      //Dump.dumpGraph(graph, "before-simplification");
      while (constantFolder.optimize(graph)
          | algebraicSimplifier.optimize(graph)
          | phiOptimizer.optimize(graph)) ;
      //Dump.dumpGraph(graph, "before-control-flow-optimizations");
      while (controlFlowOptimizer.optimize(graph) | unreachableCodeRemover.optimize(graph)) ;
      while (phiBElimination.optimize(graph) | unreachableCodeRemover.optimize(graph)) ;
    }
  }

  private void runFirm(InputStream in) throws IOException {
    Program ast = new Parser(new Lexer(in)).parse();
    ast.acceptVisitor(new SemanticAnalyzer());
    ast.acceptVisitor(new IREmitter());
    IREmitter.compileAndRun("a.out");
  }

  private void printAsm(InputStream in) throws IOException {
    Program ast = new Parser(new Lexer(in)).parse();
    ast.acceptVisitor(new SemanticAnalyzer());
    ast.acceptVisitor(new SemanticLinter());
    ast.acceptVisitor(new IREmitter());
    if (!optimizationsTurnedOff()) {
      optimize();
    }
    for (Graph g : firm.Program.getGraphs()) {
      Dump.dumpGraph(g, "--finished");
    }
    AssemblerFile file =
        AssemblerFile.createForGraphs(firm.Program.getGraphs(), SimpleNodeAllocator::new);
    if (System.getenv().containsKey("MJ_FILENAME")) {
      file.setFileName(System.getenv("MJ_FILENAME"));
    }
    out.println(file.toGNUAssembler());
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
                      == 1)
                  && (file != null)));
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
