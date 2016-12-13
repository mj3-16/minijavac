package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Node;
import minijava.ir.DefaultNodeVisitor;

public abstract class BaseOptimizer implements DefaultNodeVisitor, Optimizer {

  protected Graph graph;
  protected boolean hasChanged;

  /**
   * Uses the hasChanged property. If this is set to true in any of the visit methods then the fixed
   * point iteration goes on.
   *
   * @return true if the hasChanged property is true for at least one visit invocation, i.e. the
   *     graph has been changed
   */
  protected boolean fixedPointIteration() {
    Worklist worklist = Worklist.fillTopological(graph);
    boolean hasChangedANode = false;
    while (!worklist.isEmpty()) {
      Node n = worklist.removeFirst();
      hasChanged = false;
      n.accept(this);
      if (hasChanged) {
        hasChangedANode = true;
        for (BackEdges.Edge e : BackEdges.getOuts(n)) {
          worklist.addFirst(e.node);
        }
      }
    }
    return hasChangedANode;
  }
}
