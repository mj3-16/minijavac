package minijava;

import static firm.bindings.binding_irgraph.ir_resources_t.IR_RESOURCE_IRN_LINK;
import static minijava.Cli.dumpGraphsIfNeeded;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Runnables;
import firm.Graph;
import firm.Program;
import firm.Util;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import minijava.ir.emit.IREmitter;
import minijava.ir.optimize.*;
import minijava.ir.utils.GraphUtils;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import minijava.semantic.SemanticAnalyzer;
import minijava.semantic.SemanticLinter;
import minijava.token.Token;
import minijava.util.PrettyPrinter;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

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

  public static void optimize(int level) {
    if (level == 0) {
      // We do constant folding and algebraic simplification just before emitting code, which should
      // be enough.
      return;
    }
    dumpGraphsIfNeeded("before-optimizations");
    Optimizer constantFolder = new ConstantFolder();
    Optimizer floatInTransformation = new FloatInTransformation();
    Optimizer loopInvariantCodeMotion = new LoopInvariantCodeMotion();
    Optimizer controlFlowOptimizer = new ConstantControlFlowOptimizer();
    Optimizer jmpBlockRemover = new JmpBlockRemover();
    Optimizer unreachableCodeRemover = new UnreachableCodeRemover();
    Optimizer expressionNormalizer = new ExpressionNormalizer();
    Optimizer algebraicSimplifier = new AlgebraicSimplifier();
    Optimizer commonSubexpressionElimination = new CommonSubexpressionElimination();
    Optimizer phiOptimizer = new PhiOptimizer();
    Optimizer aliasAnalyzer = new AliasAnalyzer();
    Optimizer syncOptimizer = new SyncOptimizer();
    Optimizer loadStoreOptimizer = new LoadStoreOptimizer();
    Optimizer criticalEdgeDetector = new CriticalEdgeDetector();
    Optimizer duplicateProjDetector = new DuplicateProjDetector();

    OptimizerFramework.Builder builder =
        new OptimizerFramework.Builder()
            .add(unreachableCodeRemover)
            .dependsOn(controlFlowOptimizer, jmpBlockRemover, loopInvariantCodeMotion);

    if (EnvVar.MJ_DBG.isSetToOne()) {
      builder =
          builder
              .add(criticalEdgeDetector)
              .dependsOn(controlFlowOptimizer, jmpBlockRemover)
              .add(duplicateProjDetector)
              .dependsOn(loadStoreOptimizer, commonSubexpressionElimination);
    }

    if (level > 1) {
      builder = builder.add(syncOptimizer).dependsOn(aliasAnalyzer);
    }

    builder =
        builder
            .add(phiOptimizer)
            .dependsOn(controlFlowOptimizer, loopInvariantCodeMotion)
            .add(constantFolder)
            .dependsOn(
                algebraicSimplifier,
                phiOptimizer,
                controlFlowOptimizer,
                loadStoreOptimizer,
                loopInvariantCodeMotion)
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
                aliasAnalyzer,
                loadStoreOptimizer,
                loopInvariantCodeMotion,
                controlFlowOptimizer)
            .add(loadStoreOptimizer)
            .dependsOn(
                commonSubexpressionElimination,
                constantFolder,
                algebraicSimplifier,
                aliasAnalyzer,
                syncOptimizer)
            .add(floatInTransformation)
            .dependsOn(
                commonSubexpressionElimination,
                algebraicSimplifier,
                phiOptimizer,
                loadStoreOptimizer,
                loopInvariantCodeMotion,
                controlFlowOptimizer)
            .add(controlFlowOptimizer)
            .dependsOn(
                constantFolder, algebraicSimplifier, loadStoreOptimizer, loopInvariantCodeMotion)
            .add(jmpBlockRemover)
            .dependsOn(
                controlFlowOptimizer,
                floatInTransformation,
                loopInvariantCodeMotion,
                loadStoreOptimizer);

    if (level > 2) {
      builder =
          builder
              .add(aliasAnalyzer)
              .dependsOn(); // It's expensive to run alias analysis, so we do so only after inlining
      //.add(loopInvariantCodeMotion)
      //.dependsOn() // Dito
    }

    OptimizerFramework framework = builder.build();

    ProgramMetrics metrics = ProgramMetrics.analyse(Program.getGraphs());
    Set<Graph> intraproceduralCandidates = Sets.newHashSet(Program.getGraphs());
    Inliner inliner = new Inliner(metrics, true);
    ScheduledFuture<?> timer =
        Executors.newScheduledThreadPool(1).schedule(Runnables.doNothing(), 9, TimeUnit.MINUTES);
    while (!timer.isDone()) {
      Set<Graph> reachable = metrics.reachableFromMain();

      for (Graph graph : Sets.intersection(intraproceduralCandidates, reachable)) {
        framework.optimizeUntilFixedpoint(graph);
      }

      // Here comes the interprocedural stuff... This is method is really turning into a mess
      Cli.dumpGraphsIfNeeded("before-Inliner");
      intraproceduralCandidates.clear();
      for (Graph graph : reachable) {
        boolean hasChanged = inliner.optimize(graph);
        if (hasChanged) {
          intraproceduralCandidates.add(graph);
        }
        unreachableCodeRemover.optimize(graph);
      }

      reachable.forEach(metrics::updateGraphInfo);
      if (intraproceduralCandidates.isEmpty()) {
        if (inliner.onlyLeafs) {
          inliner = new Inliner(metrics, false);
        } else {
          break;
        }
      }
    }

    framework.logPerformanceStats();

    dumpGraphsIfNeeded("after-optimizations");
  }

  public static void produceFirmIR(InputStream in, int optimizationLevel) {
    minijava.ast.Program ast = Compiler.lexAndParse(in);
    Compiler.checkSemantics(ast);
    Compiler.verifySemanticAnnotations(ast);
    Compiler.emitIR(ast);
    if (optimizationLevel > 0) {
      optimize(optimizationLevel);
    }
  }

  public static void compile(Backend backend, String outFile, boolean produceDebuggableBinary)
      throws IOException {
    lower();
    Cli.dumpGraphsIfNeeded("after-lowering");
    String asmFile = backend.lowerToAssembler(outFile);
    assemble(asmFile, outFile, produceDebuggableBinary);
  }

  private static void lower() {
    Util.lowerSels();
    // lowering Member and Sel nodes might result in constant expressions like this + (4 * 2).
    // This shouldn't take long to rectify.
    ConstantFolder constantFolder = new ConstantFolder();
    ExpressionNormalizer normalizer = new ExpressionNormalizer();
    AlgebraicSimplifier simplifier = new AlgebraicSimplifier();
    OptimizerFramework framework =
        new OptimizerFramework.Builder()
            .add(constantFolder)
            .dependsOn()
            .add(normalizer)
            .dependsOn(constantFolder)
            .add(simplifier)
            .dependsOn(normalizer, constantFolder)
            .build();
    ProgramMetrics.analyse(Program.getGraphs())
        .reachableFromMain()
        .forEach(framework::optimizeUntilFixedpoint);
  }

  private static void assemble(
      String assemblerFile, String outputFile, boolean produceDebuggableBinary) throws IOException {
    File runtime = getRuntimeFile();

    boolean useGC = EnvVar.MJ_USE_GC.isSetToOne();

    String gccApp = "";
    if (useGC) {
      gccApp = " -DUSE_GC -lgc ";
    }

    gccApp += " " + EnvVar.MJ_GCC_APP.value();

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
      Program.getGraphs().forEach(g -> GraphUtils.freeResource(g, IR_RESOURCE_IRN_LINK));
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
      minijava.ir.assembler.Backend.lowerAssembler(outFile);
      File asm = new File(outFile + ".s");
      asm.createNewFile();
      File preAsm = new File(outFile + ".pre");
      preAsm.createNewFile();
      return asm.getName();
    }
  }
}
