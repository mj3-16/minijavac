package minijava.ir.optimize;

import firm.Graph;

public interface Optimizer {

  /**
   * Optimize the given graph
   *
   * @param graph given graph
   * @return true if the optimisation changed that passed graph
   */
  boolean optimize(Graph graph);
}
