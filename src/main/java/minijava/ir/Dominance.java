package minijava.ir;

import static org.jooq.lambda.tuple.Tuple.tuple;

import com.sun.jna.Pointer;
import firm.Graph;
import firm.bindings.binding_irdom;
import firm.nodes.Block;
import java.nio.Buffer;
import java.util.Optional;
import org.jooq.lambda.Seq;

public class Dominance {
  private static void computeDoms(Graph g) {
    binding_irdom.compute_doms(g.ptr);
  }

  private static void computePostDoms(Graph g) {
    binding_irdom.compute_postdoms(g.ptr);
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

  /** Computes the dominance frontier of {@param block}. */
  public static Block[] dominanceFrontier(Block block) {
    computeDomFrontiers(block.getGraph());
    Buffer buffer = binding_irdom.ir_get_dominance_frontier(block.ptr);
    System.out.println("buffer.getClass() = " + buffer.getClass());
    System.out.println("buffer = " + buffer.limit());
    return null;
  }
}
