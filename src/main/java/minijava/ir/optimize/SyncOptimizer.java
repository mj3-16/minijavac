package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import firm.Graph;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Node;
import firm.nodes.Sync;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;

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
    GraphUtils.topologicalOrder(graph).forEach(this::visit);
    return hasChanged;
  }

  @Override
  public void visit(Sync node) {
    Set<Node> sinks = findReachabilitySinks(node);
    Set<Node> modeMs = seq(sinks).map(NodeUtils::projModeMOf).toSet();
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

  private static Set<Node> findReachabilitySinks(Sync node) {
    // We'll use the algorithm http://wiki.c2.com/?GraphSinkDetection.
    // Note that we won't optimize beyond Phi nodes.
    Set<Node> relevantNodes = reachableSideEffects(node);
    Set<Node> possiblySources = new HashSet<>(relevantNodes);
    for (Node n : relevantNodes) {
      if (isSink(n)) {
        continue;
      }
      Node mem = n.getPred(0);
      Set<Node> notSources = NodeUtils.getPreviousSideEffects(mem);
      possiblySources.removeAll(notSources);
    }
    return possiblySources;
  }

  private static boolean isSink(Node n) {
    // We don't optimize beyond Phis.
    return n.getOpCode() == ir_opcode.iro_Start || n.getOpCode() == ir_opcode.iro_Phi;
  }

  private static Set<Node> reachableSideEffects(Sync node) {
    Set<Node> reachable = new HashSet<>();
    Deque<Node> toVisit = new ArrayDeque<>();
    toVisit.addAll(NodeUtils.getPreviousSideEffects(node));
    while (!toVisit.isEmpty()) {
      Node cur = toVisit.removeFirst();
      if (reachable.contains(cur)) {
        continue;
      }
      reachable.add(cur);
      if (!isSink(cur)) {
        Node mem = cur.getPred(0);
        toVisit.addAll(NodeUtils.getPreviousSideEffects(mem));
      }
    }
    return reachable;
  }
}
