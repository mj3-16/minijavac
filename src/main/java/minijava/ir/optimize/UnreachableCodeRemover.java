package minijava.ir.optimize;

import firm.Graph;
import firm.bindings.binding_irgopt;
import minijava.ir.Dominance;

public class UnreachableCodeRemover implements Optimizer {

  @Override
  public boolean optimize(Graph graph) {
    //Cli.dumpGraphIfNeeded(graph, "before-unreachable");

    // first remove Bad nodes we inserted manually (the remove_unreachable_code function doesn't pick them up)
    binding_irgopt.remove_bads(graph.ptr);

    // find and replace unreachable code with Bad nodes
    binding_irgopt.remove_unreachable_code(graph.ptr);

    binding_irgopt.remove_bads(graph.ptr);
    // checking whether a change on the graph occurred doesn't seem to be possible
    Dominance.invalidateDominace();
    return false;
  }
}
