package minijava.ir.optimize;

import firm.Graph;
import minijava.Cli;

public interface Optimizer {

  static void optimize() {
    Cli.dumpGraphsIfNeeded("before-optimizations");
    Optimizer constantFolder = new ConstantFolder();
    Optimizer floatInTransformation = new FloatInTransformation();
    Optimizer controlFlowOptimizer = new ConstantControlFlowOptimizer();
    Optimizer unreachableCodeRemover = new UnreachableCodeRemover();
    Optimizer expressionNormalizer = new ExpressionNormalizer();
    Optimizer algebraicSimplifier = new AlgebraicSimplifier();
    Optimizer commonSubexpressionElimination = new CommonSubexpressionElimination();
    Optimizer phiOptimizer = new PhiOptimizer();
    while (true) {
      for (Graph graph : firm.Program.getGraphs()) {
        boolean hasChanged;
        do {
          hasChanged = false;
          Cli.dumpGraphIfNeeded(graph, "before-simplification");
          while (constantFolder.optimize(graph)
              | expressionNormalizer.optimize(graph)
              | algebraicSimplifier.optimize(graph)
              | commonSubexpressionElimination.optimize(graph)
              | floatInTransformation.optimize(graph)
              | phiOptimizer.optimize(graph)) {
            hasChanged = true;
          }
          Cli.dumpGraphIfNeeded(graph, "before-control-flow-optimizations");
          while (controlFlowOptimizer.optimize(graph) | unreachableCodeRemover.optimize(graph)) {
            hasChanged = true;
          }
          //dumpGraphIfNeeded(graph, "after-constant-control-flow");
        } while (hasChanged);
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
