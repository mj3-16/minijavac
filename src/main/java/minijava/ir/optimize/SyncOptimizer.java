package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import firm.Graph;
import firm.nodes.Node;
import firm.nodes.Sync;
import java.util.Set;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import minijava.ir.utils.SideEffects;

/**
 * Performs optimizations special to Sync nodes. Currently, there are transformations for
 *
 * <ul>
 *   <li>Finding the minimal set of predecessors with the same reached set (keep only sources in the
 *       reachability graph).
 * </ul>
 */
public class SyncOptimizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    this.hasChanged = false;
    FirmUtils.withBackEdges(graph, () -> GraphUtils.topologicalOrder(graph).forEach(this::visit));
    return hasChanged;
  }

  @Override
  public void visit(Sync node) {
    Set<Node> sourceSuperset = SideEffects.getPreviousSideEffectsOrPhis(node);
    Set<Node> sources = SideEffects.filterSideEffectSources(sourceSuperset);
    Set<Node> modeMs = seq(sources).map(NodeUtils::projModeMOf).toSet();
    boolean needToExchangeWithNewNode = !seq(node.getPreds()).toSet().equals(modeMs);
    if (!needToExchangeWithNewNode) {
      return;
    }
    hasChanged = true;
    if (modeMs.size() == 1) {
      Graph.exchange(node, Iterables.getOnlyElement(modeMs));
    } else {
      Node newSync = graph.newSync(node.getBlock(), seq(modeMs).toArray(Node[]::new));
      Graph.exchange(node, newSync);
    }
  }
}
