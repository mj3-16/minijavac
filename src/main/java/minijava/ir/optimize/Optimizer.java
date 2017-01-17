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
    Optimizer phiBElimination = new PhiBElimination();
    while (true) {
      for (Graph graph : firm.Program.getGraphs()) {
        Cli.dumpGraphIfNeeded(graph, "before-simplification");
        while (constantFolder.optimize(graph)
            | expressionNormalizer.optimize(graph)
            | algebraicSimplifier.optimize(graph)
            | commonSubexpressionElimination.optimize(graph)
            | phiOptimizer.optimize(graph)) ;
        Cli.dumpGraphIfNeeded(graph, "before-control-flow-optimizations");
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
