package minijava.ir;

import static org.jooq.lambda.tuple.Tuple.tuple;

import com.sun.jna.Pointer;
import firm.bindings.binding_irdom;
import firm.nodes.Block;
import java.util.Optional;
import org.jooq.lambda.Seq;

public class Dominance {
  private static void computeDoms(Block dominator) {
    binding_irdom.compute_doms(dominator.getGraph().ptr);
  }

  public static boolean dominates(Block dominator, Block dominated) {
    computeDoms(dominator);
    return binding_irdom.block_dominates(dominator.ptr, dominated.ptr) != 0;
  }

  public static Optional<Block> immediateDominator(Block dominated) {
    computeDoms(dominated);
    Pointer idom = binding_irdom.get_Block_idom(dominated.ptr);
    if (idom.equals(Pointer.NULL)) {
      return Optional.empty();
    }
    return Optional.of(new Block(idom));
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
}
