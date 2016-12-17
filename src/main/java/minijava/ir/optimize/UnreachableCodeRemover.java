package minijava.ir.optimize;

import firm.Graph;
import firm.bindings.binding_irgopt;

public class UnreachableCodeRemover implements Optimizer {

  @Override
  public boolean optimize(Graph graph) {
    //Dump.dumpGraph(graph, "before-removal");

    binding_irgopt.remove_bads(graph.ptr);
    // find and replace unreachable code with Bad nodes
    binding_irgopt.remove_unreachable_code(graph.ptr);

    // checking whether a change on the graph occurred doesn't seem to be possible
    binding_irgopt.remove_bads(graph.ptr);
    return false;
  }
}
