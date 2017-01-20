package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.nodes.Node;
import firm.nodes.Phi;
import minijava.ir.utils.GraphUtils;

/**
 * Performs various optimizations special to Phi nodes. Currently, there are transformations for
 *
 * <ul>
 *   <li>Removing Phi nodes where all predecessors are the same.
 * </ul>
 */
public class PhiOptimizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    this.hasChanged = false;
    GraphUtils.walkPostOrder(graph, this::visit);
    return hasChanged;
  }

  @Override
  public void visit(Phi node) {
    removeSinglePredPhi(node);
  }

  private void removeSinglePredPhi(Phi node) {
    // If the predecessor edges of a Phi all point to the same node, we can eliminate that Phi.
    // That's because the definition block must domininate every predecessor, so by definition
    // of dominance it also dominates this block.
    // There is the special case where a Phi has Phis as a predecessor. We can't really do this
    // transformation if both Phis are in the same block. But if that would be the case,
    // there is no way we could enter this block, because that phi won't be visible
    // from any predecessor, at least not on the first iteration.
    long distinctPreds = seq(node.getPreds()).distinct().count();
    boolean isOnlyPred = distinctPreds == 1;
    if (!isOnlyPred) {
      return;
    }
    Node pred = node.getPred(0);

    boolean isKeptAlive = seq(graph.getEnd().getPreds()).contains(node);
    //System.out.println(isKeptAlive);
    if (isKeptAlive) {
      // We could try to find the predecessor index in End and remove it by replacing it with a Bad,
      // but that just seems like too much trouble for no gain: Kept alive nodes are (probably) always
      // Phi[loop] of mode M, which are erased by code gen.
      return;
    }

    //System.out.println(node.getGraph() + ": node " + node + ". " + Iterables.toString(seq(node.getPreds())));
    assert pred.getOpCode() != iro_Phi || !pred.getBlock().equals(node.getBlock());
    Graph.exchange(node, pred);
    hasChanged = true;
  }
}
