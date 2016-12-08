package minijava.ir.optimize;

import firm.Graph;
import firm.bindings.binding_irgopt;

public class UnreachableCodeRemover implements Optimizer {

  @Override
  public void optimize(Graph graph) {
    // first remove Bad nodes we inserted manually (the remove_unreachable_code function doesn't pick them up)
    binding_irgopt.remove_bads(graph.ptr);
    binding_irgopt.remove_unreachable_code(
        graph.ptr); // find and replace unreachable code with Bad nodes
    binding_irgopt.remove_bads(graph.ptr);
  }
}
