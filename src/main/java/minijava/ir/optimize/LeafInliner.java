package minijava.ir.optimize;

import firm.Graph;
import firm.nodes.Call;

public class LeafInliner extends Inliner {

  public LeafInliner(ProgramMetrics metrics) {
    super(metrics);
  }

  @Override
  boolean inlineCandidates() {
    boolean hasChanged = false;
    for (Call call : callsToInline) {
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
    if (!calleeInfo.calls.isEmpty()) {
      // We only inline if the callee itself doesn't call any other functions for now
      return;
    }
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
