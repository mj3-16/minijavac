package minijava;

import static minijava.Cli.dumpGraphsIfNeeded;

import com.google.common.io.Files;
import com.google.common.util.concurrent.Runnables;
import firm.ClassType;
import firm.Entity;
import firm.Graph;
import firm.MethodType;
import firm.Program;
import firm.Type;
import firm.Util;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import minijava.ir.assembler.allocator.OnTheFlyRegAllocator;
import minijava.ir.assembler.block.AssemblerFile;
import minijava.ir.emit.IREmitter;
import minijava.ir.optimize.AlgebraicSimplifier;
import minijava.ir.optimize.CommonSubexpressionElimination;
import minijava.ir.optimize.ConstantControlFlowOptimizer;
import minijava.ir.optimize.ConstantFolder;
import minijava.ir.optimize.CriticalEdgeDetector;
import minijava.ir.optimize.ExpressionNormalizer;
import minijava.ir.optimize.FloatInTransformation;
import minijava.ir.optimize.Inliner;
import minijava.ir.optimize.JmpBlockRemover;
import minijava.ir.optimize.LoadStoreOptimizer;
import minijava.ir.optimize.Optimizer;
import minijava.ir.optimize.OptimizerFramework;
import minijava.ir.optimize.PhiOptimizer;
import minijava.ir.optimize.ProgramMetrics;
import minijava.ir.optimize.UnreachableCodeRemover;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import minijava.semantic.SemanticAnalyzer;
import minijava.semantic.SemanticLinter;
import minijava.token.Token;
import minijava.util.PrettyPrinter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple2;

public class Compiler {

  public static Lexer lex(InputStream in) {
    return new Lexer(in);
  }

  public static minijava.ast.Program parse(Iterator<Token> tokens) {
    return new Parser(tokens).parse();
  }

  public static minijava.ast.Program lexAndParse(InputStream in) {
    return parse(lex(in));
  }

  public static void checkSemantics(minijava.ast.Program ast) {
    ast.acceptVisitor(new SemanticAnalyzer());
  }

  public static void emitIR(minijava.ast.Program ast) {
    ast.acceptVisitor(new IREmitter());
  }

  public static void verifySemanticAnnotations(minijava.ast.Program ast) {
    ast.acceptVisitor(new SemanticLinter());
  }

  public static void optimize() {
    dumpGraphsIfNeeded("before-optimizations");
    Optimizer constantFolder = new ConstantFolder();
    Optimizer floatInTransformation = new FloatInTransformation();
    Optimizer controlFlowOptimizer = new ConstantControlFlowOptimizer();
    Optimizer jmpBlockRemover = new JmpBlockRemover();
    Optimizer unreachableCodeRemover = new UnreachableCodeRemover();
    Optimizer expressionNormalizer = new ExpressionNormalizer();
    Optimizer algebraicSimplifier = new AlgebraicSimplifier();
    Optimizer commonSubexpressionElimination = new CommonSubexpressionElimination();
    Optimizer phiOptimizer = new PhiOptimizer();
    Optimizer loadStoreOptimizer = new LoadStoreOptimizer();
    Optimizer criticalEdgeDetector = new CriticalEdgeDetector();
    OptimizerFramework perGraphFramework =
        new OptimizerFramework.Builder()
            .add(unreachableCodeRemover)
            .dependsOn(controlFlowOptimizer, jmpBlockRemover)
            .add(criticalEdgeDetector)
            .dependsOn(controlFlowOptimizer, jmpBlockRemover)
            .add(constantFolder)
            .dependsOn(algebraicSimplifier, phiOptimizer, controlFlowOptimizer, loadStoreOptimizer)
            .add(expressionNormalizer)
            .dependsOn(
                constantFolder,
                algebraicSimplifier,
                phiOptimizer,
                commonSubexpressionElimination,
                controlFlowOptimizer,
                loadStoreOptimizer)
            .add(algebraicSimplifier)
            .dependsOn(constantFolder, phiOptimizer, controlFlowOptimizer, loadStoreOptimizer)
            .add(commonSubexpressionElimination)
            .dependsOn(
                constantFolder,
                expressionNormalizer,
                algebraicSimplifier,
                phiOptimizer,
                loadStoreOptimizer,
                controlFlowOptimizer)
            .add(loadStoreOptimizer)
            .dependsOn(commonSubexpressionElimination, constantFolder, algebraicSimplifier)
            .add(floatInTransformation)
            .dependsOn(
                commonSubexpressionElimination,
                algebraicSimplifier,
                phiOptimizer,
                loadStoreOptimizer,
                controlFlowOptimizer)
            .add(phiOptimizer)
            .dependsOn(controlFlowOptimizer)
            .add(controlFlowOptimizer)
            .dependsOn(constantFolder, algebraicSimplifier, loadStoreOptimizer)
            .add(jmpBlockRemover)
            .dependsOn(controlFlowOptimizer, floatInTransformation, loadStoreOptimizer)
            .build();

    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    Inliner inliner = new Inliner(metrics, true);
    ScheduledFuture<?> timer =
        Executors.newScheduledThreadPool(1).schedule(Runnables.doNothing(), 9, TimeUnit.MINUTES);
    while (!timer.isDone()) {
      for (Graph graph : Program.getGraphs()) {
        perGraphFramework.optimizeUntilFixedpoint(graph);
      }

      // Here comes the interprocedural stuff... This is method is really turning into a mess
      boolean hasChanged = false;
      for (Graph graph : Program.getGraphs()) {
        hasChanged |= inliner.optimize(graph);
        unreachableCodeRemover.optimize(graph);
        Cli.dumpGraphIfNeeded(graph, "after-inlining");
      }
      if (!hasChanged) {
        if (inliner.onlyLeafs) {
          inliner = new Inliner(metrics, false);
        } else {
          break;
        }
      }
    }
    dumpGraphsIfNeeded("after-optimizations");
  }

  public static void produceFirmIR(InputStream in, boolean optimize) {
    minijava.ast.Program ast = Compiler.lexAndParse(in);
    Compiler.checkSemantics(ast);
    Compiler.verifySemanticAnnotations(ast);
    Compiler.emitIR(ast);
    if (optimize) {
      optimize();
    }
    dumpGraphsIfNeeded("finished");
  }

  public static void compile(Backend backend, String outFile, boolean produceDebuggableBinary)
      throws IOException {
    lower();
    Cli.dumpGraphsIfNeeded("finished");
    String asmFile = backend.lowerToAssembler(outFile);
    assemble(asmFile, outFile, produceDebuggableBinary);
  }

  private static void lower() {
    for (Type type : Program.getTypes()) {
      if (type instanceof ClassType) {
        lowerClass((ClassType) type);
      }
    }
    Util.lowerSels();
    // lowering Member and Sel nodes might result in constant expressions like this + (4 * 2).
    // This shouldn't take long to rectify.
    ConstantFolder constantFolder = new ConstantFolder();
    Program.getGraphs().forEach(constantFolder::optimize);
  }

  /** Copied from the jFirm repo's Lower class */
  private static void lowerClass(ClassType cls) {
    for (int m = 0; m < cls.getNMembers(); /* nothing */ ) {
      Entity member = cls.getMember(m);
      Type type = member.getType();
      if (!(type instanceof MethodType)) {
        ++m;
        continue;
      }

      /* methods get implemented outside the class, move the entity */
      member.setOwner(Program.getGlobalType());
    }
  }

  private static void assemble(
      String assemblerFile, String outputFile, boolean produceDebuggableBinary) throws IOException {
    File runtime = getRuntimeFile();

    boolean useGC =
        System.getenv().containsKey("MJ_USE_GC") && System.getenv("MJ_USE_GC").equals("1");

    String gccApp = "";
    if (useGC) {
      gccApp = " -DUSE_GC -lgc ";
    }

    if (System.getenv().containsKey("MJ_GCC_APP")) {
      gccApp += " " + System.getenv("MJ_GCC_APP");
    }

    if (produceDebuggableBinary) {
      gccApp = " -g3";
    } else {
      gccApp = " -O3";
    }

    String cmd =
        String.format(
            "gcc %s %s -o %s %s", runtime.getAbsolutePath(), assemblerFile, outputFile, gccApp);
    Process p = Runtime.getRuntime().exec(cmd);
    int c;
    while ((c = p.getErrorStream().read()) != -1) {
      System.out.print(Character.toString((char) c));
    }
    int res = -1;
    try {
      res = p.waitFor();
    } catch (Throwable t) {
    }
    if (res != 0) {
      System.err.println("Warning: Assembling and linking with gcc failed");
      System.exit(1);
    }
  }

  @NotNull
  private static File getRuntimeFile() throws IOException {
    File runtime = new File(Files.createTempDir(), "mj_runtime.c");
    runtime.deleteOnExit();
    InputStream s = ClassLoader.getSystemResourceAsStream("mj_runtime.c");
    if (s == null) {
      throw new RuntimeException("");
    }
    FileUtils.copyInputStreamToFile(s, runtime);
    return runtime;
  }

  public static CharSequence prettyPrint(minijava.ast.Program ast) {
    return ast.acceptVisitor(new PrettyPrinter());
  }

  public interface Backend {
    FirmBackend FIRM = new FirmBackend();
    OwnBackend OWN = new OwnBackend();

    String lowerToAssembler(String outFile) throws IOException;
  }

  public static class FirmBackend implements Backend {
    @Override
    public String lowerToAssembler(String outFile) throws IOException {
      /* use the amd64 backend */
      firm.Backend.option("isa=amd64");
      /* transform to x86 assembler */
      String asmFile = outFile + ".s";
      firm.Backend.createAssembler(asmFile, "<builtin>");
      return asmFile;
    }
  }

  public static class OwnBackend implements Backend {
    @Override
    public String lowerToAssembler(String outFile) throws IOException {
      File asm = new File(outFile + ".s");
      asm.createNewFile();
      File preAsm = new File(outFile + ".pre");
      preAsm.createNewFile();
      printAsm(new FileOutputStream(asm), Optional.of(new FileOutputStream(preAsm)));
      return asm.getName();
    }

    public void printAsm(OutputStream out, Optional<OutputStream> preAsmOut) {
      Tuple2<AssemblerFile, AssemblerFile> preAsmAndAsmFile =
          AssemblerFile.createForProgram(OnTheFlyRegAllocator::new);
      AssemblerFile preAsmFile = preAsmAndAsmFile.v1;
      new PrintStream(preAsmOut.orElse(System.err)).println(preAsmFile.toGNUAssembler());
      AssemblerFile file = preAsmAndAsmFile.v2;
      if (System.getenv().containsKey("MJ_FILENAME")) {
        preAsmFile.setFileName(System.getenv("MJ_FILENAME"));
        file.setFileName(System.getenv("MJ_FILENAME"));
      }
      new PrintStream(out).println(file.toGNUAssembler());
    }
  }
}
