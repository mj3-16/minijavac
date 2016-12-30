package minijava.ir;

import firm.bindings.binding_irdom;
import firm.nodes.Block;

public class Dominance {
  public static boolean dominates(Block dominator, Block dominated) {
    binding_irdom.compute_doms(dominator.getGraph().ptr);
    return binding_irdom.block_dominates(dominator.ptr, dominated.ptr) != 0;
  }
}
