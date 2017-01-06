package minijava.ir.optimize;

import firm.Graph;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.NodeVisitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProgramMetrics {
  public final Map<Graph, GraphInfo> graphInfos = new HashMap<>();

  public static ProgramMetrics analyse(Iterable<Graph> graphs) {
    ProgramMetrics metrics = new ProgramMetrics();
    for (Graph graph : graphs) {
      GraphWalker walker = new GraphWalker();
      graph.walk(walker);
      metrics.graphInfos.put(graph, walker.resultSummary());
    }
    return metrics;
  }

  public static class GraphInfo {
    public final Set<Graph> calls;

    public GraphInfo(Set<Graph> calls) {
      this.calls = calls;
    }
  }

  private static class GraphWalker extends NodeVisitor.Default {
    private final Set<Graph> calls = new HashSet<>();

    @Override
    public void visit(Call node) {
      Address funcPtr = (Address) node.getPtr();
      Graph callee = funcPtr.getEntity().getGraph();
      if (callee == null) {
        // This was a foreign/native call
        return;
      }
      calls.add(callee);
    }

    public GraphInfo resultSummary() {
      return new GraphInfo(calls);
    }
  }
}
