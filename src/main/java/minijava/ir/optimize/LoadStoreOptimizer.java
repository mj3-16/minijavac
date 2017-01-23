package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.nodes.*;
import minijava.ir.Dominance;
import minijava.ir.utils.GraphUtils;

public class LoadStoreOptimizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    return fixedPointIteration(GraphUtils.reversePostOrder(graph));
  }

  @Override
  public void visit(Proj node) {
    // We have to propagate changes in the fixed-point iteration through Projs
    hasChanged = true;
  }

  @Override
  public void visit(Load node) {
    Node lastSideEffect = node.getMem().getPred(0);
    switch (lastSideEffect.getOpCode()) {
      case iro_Load:
        Load previousLoad = (Load) lastSideEffect;
        if (!previousLoad.getPtr().equals(node.getPtr())) {
          break;
        }

        hasChanged = true;
        for (BackEdges.Edge be : BackEdges.getOuts(node)) {
          // Let the Projs point to the previous load
          be.node.setPred(be.pos, previousLoad);
          be.node.setBlock(previousLoad.getBlock());
        }
        Graph.killNode(node);
        break;
      case iro_Store:
        Store previousStore = (Store) lastSideEffect;
        if (!previousStore.getPtr().equals(node.getPtr())) {
          break;
        }

        hasChanged = true;
        for (BackEdges.Edge be : BackEdges.getOuts(node)) {
          if (be.node.getMode().equals(Mode.getM())) {
            // Let this Proj M point to the Store instead
            be.node.setPred(be.pos, previousStore);
            be.node.setBlock(previousStore.getBlock());
          } else {
            // And replace the data Projs by the stored value
            Graph.exchange(be.node, previousStore.getValue());
          }
        }
        Graph.killNode(node);
        break;
      default:
        break;
    }
  }

  @Override
  public void visit(Store node) {
    Node lastSideEffect = node.getMem().getPred(0);
    switch (lastSideEffect.getOpCode()) {
      case iro_Store:
        Store previousStore = (Store) lastSideEffect;
        if (!previousStore.getPtr().equals(node.getPtr())) {
          break;
        }

        hasChanged = true;
        node.setMem(previousStore.getMem());
        boolean killsOldValue =
            Dominance.postDominates((Block) node.getBlock(), (Block) previousStore.getBlock());
        if (killsOldValue) {
          Graph.killNode(previousStore);
        }

        break;
      default:
        break;
    }
  }
}
