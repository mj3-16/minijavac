package minijava.ir.optimize;

import firm.Graph;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.End;
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
      metrics.updateGraphInfo(graph);
    }
    return metrics;
  }

  public void updateGraphInfo(Graph graph) {
    GraphWalker walker = new GraphWalker();
    graph.walk(walker);
    graphInfos.put(graph, walker.resultSummary());
  }

  public static class GraphInfo {
    public final Set<Graph> calls;
    public final boolean diverges;

    public GraphInfo(Set<Graph> calls, boolean diverges) {
      this.calls = calls;
      this.diverges = diverges;
    }
  }

  private static class GraphWalker extends NodeVisitor.Default {
    private final Set<Graph> calls = new HashSet<>();
    private boolean diverges = true;

    @Override
    public void visit(End node) {
      // The only possible (optional) predecessors for an end node are a Phi[loop] node at index 0, indicating
      // a possibly diverging loop, and a to-be-kept-alive block at index 1.
      //
      // If a graph certainly diverges, its end node will have both predecessors (possibly more, I don't know).
      diverges = node.getPredCount() >= 2;
    }

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
      return new GraphInfo(calls, diverges);
    }
  }
}
