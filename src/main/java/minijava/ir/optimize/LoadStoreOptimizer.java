package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.BackEdges.Edge;
import firm.Graph;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Load;
import firm.nodes.Node;
import firm.nodes.Proj;
import firm.nodes.Store;
import java.util.List;
import minijava.ir.Dominance;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;

public class LoadStoreOptimizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    return fixedPointIteration(GraphUtils.topologicalOrder(graph));
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
        // This will not happen any more. After the AliasAnalyzer, there shouldn't be any
        // Load-Load dependencies. This case is thus handled by CSE.
        // I leave it here anyway for when the alias analysis isn't run.
        Load previousLoad = (Load) lastSideEffect;
        if (!previousLoad.getPtr().equals(node.getPtr())) {
          break;
        }

        hasChanged = true;

        // There must never be more than one Proj for each Num (e.g. M, Res, etc.),
        // so we merge them.
        NodeUtils.redirectProjsOnto(node, previousLoad);
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
        NodeUtils.mergeProjsWithNum(previousStore, Store.pnM);
        Graph.killNode(node);
        break;
      default:
        break;
    }
  }

  @Override
  public void visit(Store currentStore) {
    Node lastSideEffect = currentStore.getMem().getPred(0);
    switch (lastSideEffect.getOpCode()) {
      case iro_Store:
        Store previousStore = (Store) lastSideEffect;
        if (!previousStore.getPtr().equals(currentStore.getPtr())) {
          break;
        }

        // We may only eliminate the store if it isn't visible any more, e.g. currentStore
        // is the only successor to previousStore.
        Node projOnPrevious = currentStore.getMem();
        List<Edge> usages =
            seq(BackEdges.getOuts(projOnPrevious))
                .filter(be -> !be.node.equals(currentStore))
                .toList();
        Block currentBlock = (Block) currentStore.getBlock();
        boolean dominatesAllUsages =
            seq(usages)
                .allMatch(be -> Dominance.dominates(currentBlock, (Block) be.node.getBlock()));
        if (!dominatesAllUsages) {
          // We can't do the transformation, as the old stored value might leak through.
          break;
        }

        hasChanged = true;
        Proj projOnCurrent = NodeUtils.getMemProjSuccessor(currentStore);
        for (Edge usage : usages) {
          usage.node.setPred(usage.pos, projOnCurrent);
        }
        currentStore.setMem(previousStore.getMem());
        Graph.killNode(previousStore);
        Graph.killNode(projOnPrevious);
        break;
      default:
        break;
    }
  }
}
