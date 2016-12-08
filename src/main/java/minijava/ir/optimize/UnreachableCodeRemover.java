package minijava.ir.optimize;

import firm.Graph;
import firm.bindings.binding_irgopt;

public class UnreachableCodeRemover implements Optimizer {

  @Override
  public void optimize(Graph graph) {
    binding_irgopt.remove_unreachable_code(graph.ptr);
    binding_irgopt.remove_bads(graph.ptr);
  }
}
