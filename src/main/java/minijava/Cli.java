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
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import minijava.ast.Program;
import minijava.ir.IREmitter;
import minijava.ir.assembler.block.AssemblerFile;
import minijava.ir.optimize.*;
import minijava.ir.utils.CompileUtils;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import minijava.semantic.SemanticAnalyzer;
import minijava.semantic.SemanticLinter;
import minijava.token.Token;
import minijava.util.PrettyPrinter;
import org.jooq.lambda.tuple.Tuple2;

public class Cli {

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
        compileFirm(in);
      } else if (params.runFirm) {
        runFirm(in);
      } else if (params.printAsm) {
        printAsm(in);
      } else {
        compile(in);
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
    dumpGraphsIfNeeded("--finished");
    IREmitter.compile("a.out", shouldProduceDebuggableBinary());
  }

  private boolean optimizationsTurnedOff() {
    String value = System.getenv("MJ_OPTIMIZE");
    return value != null && value.equals("0");
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

  private void optimize() {
    dumpGraphsIfNeeded("before-optimizations");
    Optimizer constantFolder = new ConstantFolder();
    Optimizer floatInTransformation = new FloatInTransformation();
    Optimizer controlFlowOptimizer = new ConstantControlFlowOptimizer();
    Optimizer unreachableCodeRemover = new UnreachableCodeRemover();
    Optimizer expressionNormalizer = new ExpressionNormalizer();
    Optimizer algebraicSimplifier = new AlgebraicSimplifier();
    Optimizer commonSubexpressionElimination = new CommonSubexpressionElimination();
    Optimizer phiOptimizer = new PhiOptimizer();
    Optimizer phiBElimination = new PhiBElimination();
    while (true) {
      for (Graph graph : firm.Program.getGraphs()) {
        dumpGraphIfNeeded(graph, "before-simplification");
        while (constantFolder.optimize(graph)
            | expressionNormalizer.optimize(graph)
            | algebraicSimplifier.optimize(graph)
            | commonSubexpressionElimination.optimize(graph)
            | phiOptimizer.optimize(graph)) ;
        dumpGraphIfNeeded(graph, "before-control-flow-optimizations");
        while (controlFlowOptimizer.optimize(graph) | unreachableCodeRemover.optimize(graph)) ;
        //dumpGraphIfNeeded(graph, "after-constant-control-flow");
        while (phiBElimination.optimize(graph) | unreachableCodeRemover.optimize(graph)) ;
        while (floatInTransformation.optimize(graph)
            | commonSubexpressionElimination.optimize(graph)) ;
      }

      // Here comes the interprocedural stuff... This is method is really turning into a mess
      boolean hasChanged = false;
      ProgramMetrics metrics = ProgramMetrics.analyse(firm.Program.getGraphs());
      Optimizer inliner = new Inliner(metrics);
      for (Graph graph : firm.Program.getGraphs()) {
        hasChanged |= inliner.optimize(graph);
        unreachableCodeRemover.optimize(graph);
        metrics.updateGraphInfo(graph);
        Cli.dumpGraphIfNeeded(graph, "after-inlining");
      }
      if (!hasChanged) {
        break;
      }
    }
    dumpGraphsIfNeeded("after-optimizations");
  }

  private void runFirm(InputStream in) throws IOException {
    Program ast = new Parser(new Lexer(in)).parse();
    ast.acceptVisitor(new SemanticAnalyzer());
    ast.acceptVisitor(new IREmitter());
    dumpGraphsIfNeeded("finished");
    IREmitter.compileAndRun("a.out", shouldProduceDebuggableBinary());
  }

  private void printAsm(InputStream in) throws IOException {
    printAsm(in, Optional.empty(), out);
  }

  private void printAsm(InputStream in, Optional<PrintStream> preAsmOut, PrintStream out)
      throws IOException {
    Program ast = new Parser(new Lexer(in)).parse();
    ast.acceptVisitor(new SemanticAnalyzer());
    ast.acceptVisitor(new SemanticLinter());
    ast.acceptVisitor(new IREmitter());
    if (!optimizationsTurnedOff()) {
      optimize();
    }
    dumpGraphsIfNeeded("--finished");
    Tuple2<AssemblerFile, AssemblerFile> preAsmAndAsmFile = AssemblerFile.createForProgram();
    AssemblerFile preAsmFile = preAsmAndAsmFile.v1;
    if (!preAsmOut.isPresent()) {
      preAsmOut = Optional.of(System.err);
    }
    preAsmOut.ifPresent(o -> o.println(preAsmFile.toGNUAssembler()));
    AssemblerFile file = preAsmAndAsmFile.v2;
    if (System.getenv().containsKey("MJ_FILENAME")) {
      preAsmFile.setFileName(System.getenv("MJ_FILENAME"));
      file.setFileName(System.getenv("MJ_FILENAME"));
    }
    out.println(file.toGNUAssembler());
  }

  private void compile(InputStream in) throws IOException {
    File file = new File("a.out.s");
    file.createNewFile();
    File preAsmFile = new File("a.out.pre");
    preAsmFile.createNewFile();
    printAsm(
        in,
        Optional.of(new PrintStream(new FileOutputStream(preAsmFile))),
        new PrintStream(new FileOutputStream(file)));
    CompileUtils.compileAssemblerFile("a.out.s", "a.out", shouldProduceDebuggableBinary());
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
                      <= 1)
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
