package minijava.ir.optimize;

import firm.Graph;
import firm.nodes.Phi;

/**
 * Performs various optimizations special to Phi nodes. Currently, there are transformations for
 *
 * <ul>
 *   <li>Removing Phi nodes with only a single predecessor
 * </ul>
 */
public class PhiOptimizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    this.hasChanged = false;
    graph.walkTopological(this);
    return hasChanged;
  }

  @Override
  public void visit(Phi node) {
    removeSinglePredPhi(node);
  }

  private void removeSinglePredPhi(Phi node) {
    // If a block only has a single predecessor, the predecessor's block is the immediate dominator.
    // We can freely use any values from that block, including the predecessor itself, with which we replace the Phi.
    if (node.getPredCount() == 1) {
      Graph.exchange(node, node.getPred(0));
    }
  }
}
