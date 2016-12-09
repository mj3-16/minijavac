package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Node;

public abstract class BaseOptimizer extends DefaultNodeVisitor implements Optimizer {

  protected Graph graph;
  protected boolean hasChanged;

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
