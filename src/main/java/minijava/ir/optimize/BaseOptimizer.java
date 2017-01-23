package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Deleted;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import java.util.Collection;
import minijava.ir.utils.FirmUtils;

public abstract class BaseOptimizer extends NodeVisitor.Default implements Optimizer {

  protected Graph graph;
  protected boolean hasChanged;

  /**
   * Implements the work-list algorithm, populating the initial worklist by passing .
   *
   * <p>Nodes in the work-list are processed by calling {@link Node#accept(NodeVisitor)
   * node.accept(this)}. In order for the data-flow analyses to be successful, implementations of
   * {@link BaseOptimizer} <b>must</b> set the protected {@code hasChanged} field to true in the
   * overwritten {@code visit(Node)} methods, if the outputs of the node's transfer function differ
   * from the outputs of the previous visiting.
   */
  protected boolean fixedPointIteration(Collection<Node> initialWorklist) {
    return FirmUtils.withBackEdges(
        graph,
        () -> {
          boolean hasChangedAtAll = false;
          Worklist worklist = new Worklist(initialWorklist);
          while (!worklist.isEmpty()) {
            Node n = worklist.dequeue();
            if (n.getOpCode() == iro_Deleted) {
              continue;
            }
            hasChanged = false;
            n.accept(this);
            if (hasChanged) {
              for (BackEdges.Edge e : BackEdges.getOuts(n)) {
                worklist.enqueue(e.node);
              }
              hasChangedAtAll = true;
            }
          }
          return hasChangedAtAll;
        });
  }

  /** Just a helper method, Node.accept(NodeVisitor) flipped. */
  protected void visit(Node node) {
    node.accept(this);
  }
}
