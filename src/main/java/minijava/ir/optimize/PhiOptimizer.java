package minijava.ir.optimize;

import firm.Graph;
import firm.TargetValue;
import firm.nodes.*;

/**
 * Performs various optimizations special to Phi nodes. Currently, there are transformations for
 *
 * <ul>
 *   <li>Identifying constant inputs to Phi nodes based on control flow
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
    findControlFlowDependentConstants(node);
  }

  private void removeSinglePredPhi(Phi node) {
    // If a block only has a single predecessor, the predecessor's block is the immediate dominator.
    // We can freely use any values from that block, including the predecessor itself, with which we replace the Phi.
    if (node.getPredCount() == 1) {
      Graph.exchange(node, node.getPred(0));
    }
  }

  private void findControlFlowDependentConstants(Phi node) {
    // This tries to find constant input to phi nodes, based matched on expressions in predecessor blocks.
    // Detecting and eliminating these 'common subexpression' is crucial for Phi b elimination to kick in
    // and optimize away Phi b nodes.
    // These Phis occur when doing shortcircuiting: The IREmitter encodes booleans as mode `b` nodes,
    // which requires shortcircuiting to return these Phi b nodes.
    int n = node.getPredCount();
    Block currentBlock = (Block) node.getBlock();
    for (int i = 0; i < n; i++) {
      // Try to utilize control flow information to deduce constness of a previously matched expression
      Node pred = node.getPred(i);
      // Find out from where we entered this block.
      try {
        // Also, we are only interested in conditional jumps, as that kind
        // of control flow might reveal the constant value of the selected expression.
        Proj jmpSource = (Proj) currentBlock.getPred(i); // might throw a ClassCastException
        Cond cond = (Cond) jmpSource.getPred();
        Node selector = cond.getSelector();
        if (selector.equals(pred)) {
          hasChanged = true;
          // This is the interesting case.
          // The cond node matched on one of the preds of this phi, so we can
          // recover the constant value of the selector from the control flow.
          TargetValue targetValue =
              jmpSource.getNum() == Cond.pnTrue ? TargetValue.getBTrue() : TargetValue.getBFalse();
          node.setPred(i, graph.newConst(targetValue));
        }
      } catch (ClassCastException e) {
        // Some other kind of jump, e.g. Jmp.
      }
    }
  }
}
