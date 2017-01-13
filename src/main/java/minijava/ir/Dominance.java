package minijava.ir;

import firm.Graph;
import firm.bindings.binding_irdom;
import firm.nodes.Block;

public class Dominance {
  private static void computeDoms(Graph g) {
    binding_irdom.compute_doms(g.ptr);
  }

  public static boolean dominates(Block dominator, Block dominated) {
    computeDoms(dominator.getGraph());
    return binding_irdom.block_dominates(dominator.ptr, dominated.ptr) != 0;
  }
}
