package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;

public abstract class BaseOptimizer extends NodeVisitor.Default implements Optimizer {

  protected Graph graph;
  protected boolean hasChanged;

  /**
   * Implements the work-list algorithm.
   *
   * <p>Nodes in the work-list are processed by calling {@link Node#accept(NodeVisitor)
   * node.accept(this)}. In order for the data-flow analyses to be successful, implementations of
   * {@link BaseOptimizer} <b>must</b> set the protected {@code hasChanged} field to true in the
   * overwritten {@code visit(Node)} methods, if the outputs of the node's transfer function differ
   * from the outputs of the previous visiting.
   */
  protected void fixedPointIteration() {
    Worklist worklist = Worklist.fillTopological(graph);
    while (!worklist.isEmpty()) {
      Node n = worklist.removeFirst();
      hasChanged = false;
      n.accept(this);
      if (hasChanged) {
        for (BackEdges.Edge e : BackEdges.getOuts(n)) {
          worklist.addFirst(e.node);
        }
      }
    }
  }

  /** Just a helper method, Node.accept(NodeVisitor) flipped. */
  protected void visit(Node node) {
    node.accept(this);
  }
}
