package minijava.ir.optimize;

import com.google.common.util.concurrent.Runnables;
import firm.Graph;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import minijava.Cli;

public interface Optimizer {

  static void optimize() {
    Cli.dumpGraphsIfNeeded("before-optimizations");
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

    ProgramMetrics metrics = ProgramMetrics.analyse(firm.Program.getGraphs());
    Inliner inliner = new Inliner(metrics, true);
    ScheduledFuture<?> timer =
        Executors.newScheduledThreadPool(1).schedule(Runnables.doNothing(), 9, TimeUnit.MINUTES);
    while (!timer.isDone()) {
      for (Graph graph : firm.Program.getGraphs()) {
        perGraphFramework.optimizeUntilFixedpoint(graph);
      }

      // Here comes the interprocedural stuff... This is method is really turning into a mess
      boolean hasChanged = false;
      for (Graph graph : firm.Program.getGraphs()) {
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
    Cli.dumpGraphsIfNeeded("after-optimizations");
  }

  /**
   * Optimize the given graph
   *
   * @param graph given graph
   * @return true if the optimisation changed that passed graph
   */
  boolean optimize(Graph graph);
}
