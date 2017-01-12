package minijava.ir.optimize;

import firm.Graph;
import firm.TargetValue;
import firm.nodes.*;

/**
 * Performs various optimizations special to Phi nodes. Currently, there are transformations for
 *
 * <p>- Optimizing Phi-of-Phis within the same block
 *
 * <p>- Identifying constant inputs to Phi nodes based on control flow
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
    followPhiNodesInSameBlock(node);
    findControlFlowDependentConstants(node);
  }

  private void followPhiNodesInSameBlock(Phi node) {
    int n = node.getPredCount();
    for (int i = 0; i < n; ++i) {
      if (!(node.getPred(i) instanceof Phi)) {
        continue;
      }
      Phi phi = (Phi) node.getPred(i);
      if (phi.equals(node) || phi.equals(phi.getPred(i))) {
        continue;
      }
      boolean bothPhisInSameBlock = phi.getBlock().equals(node.getBlock());
      if (!bothPhisInSameBlock) {
        continue;
      }
      node.setPred(i, phi.getPred(i));
      hasChanged = true;
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
