package minijava.ir.utils;

import static org.jooq.lambda.tuple.Tuple.tuple;

import com.sun.jna.Pointer;
import firm.Graph;
import firm.bindings.binding_irdom;
import firm.nodes.Block;
import java.util.Optional;
import org.jooq.lambda.Seq;

public class Dominance {
  private static boolean needsToRecomputeDominance = false;
  private static boolean needsToRecomputePostDominance = false;

  public static void invalidateDominace() {
    needsToRecomputeDominance = true;
    needsToRecomputePostDominance = true;
  }

  private static void computeDoms(Graph g) {
    if (needsToRecomputeDominance) {
      binding_irdom.compute_doms(g.ptr);
      needsToRecomputeDominance = false;
    }
  }

  private static void computePostDoms(Graph g) {
    if (needsToRecomputePostDominance) {
      binding_irdom.compute_postdoms(g.ptr);
      needsToRecomputePostDominance = false;
    }
  }

  private static void computeDomFrontiers(Graph g) {
    binding_irdom.ir_compute_dominance_frontiers(g.ptr);
  }

  public static boolean dominates(Block dominator, Block dominated) {
    computeDoms(dominator.getGraph());
    return binding_irdom.block_dominates(dominator.ptr, dominated.ptr) != 0;
  }

  public static Optional<Block> immediateDominator(Block dominated) {
    computeDoms(dominated.getGraph());
    Pointer idom = binding_irdom.get_Block_idom(dominated.ptr);
    if (idom == null) {
      return Optional.empty();
    }
    return Optional.of(new Block(idom));
  }

  public static boolean strictlyDominates(Block dominator, Block dominated) {
    return !dominator.equals(dominated) && dominates(dominator, dominated);
  }

  /** The reflexive transitive path of immediate dominators starting from {@param dominated}. */
  public static Seq<Block> dominatorPath(Block dominated) {
    return Seq.of(dominated)
        .concat(
            Seq.unfold(
                dominated,
                b -> {
                  Optional<Block> optIdom = immediateDominator(b);
                  return optIdom.map(idom -> tuple(idom, idom));
                }));
  }

  public static boolean postDominates(Block dominator, Block dominated) {
    computePostDoms(dominator.getGraph());
    return binding_irdom.block_postdominates(dominator.ptr, dominated.ptr) != 0;
  }

  public static boolean strictlyPostDominates(Block dominator, Block dominated) {
    return !dominator.equals(dominated) && postDominates(dominator, dominated);
  }

  public static Block deepestCommonDominator(Block a, Block b) {
    computeDoms(a.getGraph());
    return new Block(binding_irdom.ir_deepest_common_dominator(a.ptr, b.ptr));
  }
}
