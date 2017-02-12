package minijava.ir.utils;

import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Sets;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.Phi;
import firm.nodes.Start;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import minijava.ir.Dominance;
import minijava.ir.optimize.AliasAnalyzer;
import org.jooq.lambda.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SideEffects {
  private static final Logger LOGGER = LoggerFactory.getLogger("SideEffects");
  private static final int MAX_NUMBER_OF_SYNC_PREDS = 10;

  /**
   * This is the meat of the Alias transformation. It got somewhat complicated when adding support
   * for Phi nodes with something like back-tracking.
   *
   * @param affectsRoot Determines for a given side effecting node if we can ignore it and continue
   *     to search its predecessors. This will compare alias classes for overlap in {@link
   *     AliasAnalyzer}.
   * @param root The memory nodes from which to start searching for affecting side effects.
   * @return The set of all last affecting side effects. These may be multiple, because of Sync
   *     nodes.
   */
  public static Set<Node> lastAffectingSideEffects(Predicate<Node> affectsRoot, Node root) {
    // Assumes that the single mem predecessor is at index 0.
    Node mem = root.getPred(0);
    Set<Node> roots = getPreviousSideEffectsOrPhis(mem);
    Set<Node> visited = Sets.newHashSet(root);
    Set<Node> sourceSuperset =
        lastAffectingSideEffectsHelper(affectsRoot, roots, visited).sideEffects;
    return filterSideEffectSources(sourceSuperset);
  }

  /**
   * This is the meat of lastAliasingSideEffects. It got somewhat complicated when adding support
   * for Phi nodes with something like back-tracking.
   *
   * @param canMoveBeyond Determines for a given side effecting node if we can ignore it and
   *     continue to search its predecessors. This will compare alias classes for overlap in {@link
   *     AliasAnalyzer}.
   * @param roots The memory nodes from which to start searching for aliases.
   * @param originalVisited The set of already visited nodes at the time this call happens.
   * @return A LastAliasingSideEffectsResult containing the last aliasing side effects and the set
   *     of nodes visited during the search.
   */
  private static LastAliasingSideEffectsResult lastAffectingSideEffectsHelper(
      Predicate<Node> canMoveBeyond, Set<Node> roots, Set<Node> originalVisited) {
    Set<Node> ret = new HashSet<>();
    Set<Node> toVisit = new HashSet<>(roots);
    Set<Node> visited = new HashSet<>(originalVisited);
    while (!toVisit.isEmpty()) {
      Node prevSideEffect = toVisit.iterator().next();
      toVisit.remove(prevSideEffect);
      if (visited.contains(prevSideEffect)) {
        continue;
      }
      visited.add(prevSideEffect);

      if (prevSideEffect instanceof Start) {
        // We can't extend beyond a Start node.
        ret.add(prevSideEffect);
      } else if (prevSideEffect instanceof Phi) {
        // We try to extend beyond that Phi
        Set<Node> sideEffectsPrecedingPhi =
            seq(prevSideEffect.getPreds())
                .map(SideEffects::getPreviousSideEffectsOrPhis)
                .flatMap(Seq::seq)
                .toSet();
        LastAliasingSideEffectsResult extended =
            lastAffectingSideEffectsHelper(canMoveBeyond, sideEffectsPrecedingPhi, visited);
        // The extended solution might contain side effects from the current block, reachable
        // through a back edge. If there is such a node, we can't extend beyond the Phi.
        // Also, if one of the side effects doesn't dominate the current block, we can't extend.
        // In other words: All side effects' blocks must be strict dominators of the Phi's block.
        // Note that it's OK if this returned the node we started from and that case is the
        // reason we pass along the visited set: So that no node already visited is included (as
        // we already have them covered).
        Block phiBlock = (Block) prevSideEffect.getBlock();
        boolean canExtend =
            seq(extended.sideEffects)
                .allMatch(se -> Dominance.strictlyDominates((Block) se.getBlock(), phiBlock));
        if (!canExtend) {
          // Just record the Phi.
          ret.add(prevSideEffect);
        } else {
          ret.addAll(extended.sideEffects);
          // This is only OK now that we covered all those nodes.
          visited = extended.visited;
        }
      } else if (canMoveBeyond.test(prevSideEffect)) {
        // We can't extend beyond the aliasing node.
        ret.add(prevSideEffect);
      } else {
        // The node doesn't alias, so we try its side-effecting parents.
        Node prevMem = prevSideEffect.getPred(0);
        Set<Node> finalVisited = visited;
        seq(getPreviousSideEffectsOrPhis(prevMem))
            .filter(n -> !finalVisited.contains(n))
            .forEach(toVisit::add);
      }

      if (toVisit.size() > Math.max(MAX_NUMBER_OF_SYNC_PREDS, roots.size())) {
        // This is a conservative default, to speed up compilation time and space.
        return new LastAliasingSideEffectsResult(roots, originalVisited);
      }
    }
    return new LastAliasingSideEffectsResult(ret, visited);
  }

  /**
   * Filters out side effects from {@param sourceSuperset} which are already reachable through some
   * other source in the set.
   */
  public static Set<Node> filterSideEffectSources(Set<Node> sourceSuperset) {
    // We'll use the algorithm http://wiki.c2.com/?GraphSinkDetection.
    // Note that we won't optimize beyond Phi nodes.
    Set<Node> relevantNodes = reachableSideEffects(sourceSuperset);
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

  private static boolean isSink(Node n) {
    // We don't optimize beyond Phis.
    return n instanceof Start;
  }

  private static Set<Node> reachableSideEffects(Set<Node> sources) {
    Set<Node> reachable = new HashSet<>();
    Deque<Node> toVisit = new ArrayDeque<>();
    toVisit.addAll(sources);
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

  private static Seq<Set<Node>> getPrecedingSideEffects(Node n) {
    Set<Node> mems;
    if (n instanceof Phi) {
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

    return seq(mems).map(SideEffects::getPreviousSideEffectsOrPhis);
  }

  /**
   * Expects {@param modeM} to be a Mem node from which we follow mem edges until we hit a
   * side-effect node (including Phis). Essentially skips uninteresting Proj M and Sync nodes.
   */
  public static Set<Node> getPreviousSideEffectsOrPhis(Node modeM) {
    assert modeM.getMode().equals(Mode.getM());
    switch (modeM.getOpCode()) {
      case iro_Proj:
        return Sets.newHashSet(modeM.getPred(0));
      case iro_Phi:
        return Sets.newHashSet(modeM); // we return Phis themselves
      case iro_Sync:
        Set<Node> ret = new HashSet<>();
        seq(modeM.getPreds()).map(SideEffects::getPreviousSideEffectsOrPhis).forEach(ret::addAll);
        return ret;
      default:
        LOGGER.warn("Didn't expect a mode M node " + modeM);
        return Sets.newHashSet(modeM);
    }
  }

  private static class LastAliasingSideEffectsResult {

    public final Set<Node> sideEffects;
    public final Set<Node> visited;

    public LastAliasingSideEffectsResult(Set<Node> sideEffects, Set<Node> visited) {
      this.sideEffects = sideEffects;
      this.visited = visited;
    }
  }
}
