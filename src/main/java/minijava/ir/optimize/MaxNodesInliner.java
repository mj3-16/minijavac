package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.*;

import firm.Graph;
import firm.nodes.*;

/**
 * Inlines method calls into the graph as long as the number of nodes in the graph is smaller than
 * the maximum size.
 */
public class MaxNodesInliner extends Inliner {

  static final int MAX_SIZE = 10000;

  public MaxNodesInliner(ProgramMetrics metrics) {
    super(metrics);
  }

  @Override
  boolean inlineCandidates() {
    boolean hasChanged = false;
    int size = metrics.graphInfos.get(graph).size;
    for (Call call : callsToInline) {
      size += metrics.graphInfos.get(call.getGraph()).size;
      if (size > MaxNodesInliner.MAX_SIZE) {
        return hasChanged;
      }
      inline(call);
      hasChanged = true;
    }
    return hasChanged;
  }

  @Override
  public void visit(Call call) {
    Graph callee = callee(call);
    if (callee == null) {
      // This was a foreign/native call, where we don't have access to the graph.
      return;
    }
    ProgramMetrics.GraphInfo calleeInfo = metrics.graphInfos.get(callee(call));
    if (calleeInfo.diverges) {
      // We could potentially inline this, but it won't bring any benefit, as the diverging loop
      // dominates the method call overhead.
      // In case we do want this though, we have to graph.keepAlive(end.getPred(1));
      return;
    }
    if (calleeInfo.size > 1000) {
      // This should be sufficiently high so that the call overhead isn't noticeable
      // (generally, imagine an if/else...).
      return;
    }
    callsToInline.add(call);
  }
}
