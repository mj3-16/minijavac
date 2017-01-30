package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import firm.Graph;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.Sync;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import minijava.ir.Dominance;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import org.jooq.lambda.Seq;

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
      // Any side effect with an incoming edge is not a source
      getPrecedingSideEffects(n).forEach(possiblySources::removeAll);
    }
    return possiblySources;
  }

  private static Seq<Set<Node>> getPrecedingSideEffects(Node n) {
    Set<Node> mems;
    if (n.getOpCode() == iro_Phi) {
      // We have to be careful to not follow back edges. We might reach actual sources through
      // them, so that they aren't sources anymore.
      // So we only return those mems where the blocks aren't dominated by the Phi's.
      Block phiBlock = (Block) n.getBlock();
      mems =
          seq(n.getPreds())
              .filter(mem -> !Dominance.dominates(phiBlock, (Block) mem.getBlock()))
              .toSet();
    } else {
      // Every other case is simple: Just use the single mem pred at index 0.
      mems = Sets.newHashSet(n.getPred(0));
    }

    return seq(mems).map(NodeUtils::getPreviousSideEffects);
  }

  private static boolean isSink(Node n) {
    // We don't optimize beyond Phis.
    return n.getOpCode() == ir_opcode.iro_Start;
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
        getPrecedingSideEffects(cur).forEach(toVisit::addAll);
      }
    }
    return reachable;
  }
}
