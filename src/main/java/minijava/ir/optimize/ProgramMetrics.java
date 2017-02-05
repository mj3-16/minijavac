package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.Program;
import firm.nodes.Address;
import firm.nodes.Call;
import firm.nodes.End;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import minijava.ir.emit.NameMangler;

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

  public Set<Graph> reachableFromMain() {
    Set<Graph> reachable = new HashSet<>();
    Set<Graph> toVisit = new HashSet<>();
    Graph main = seq(Program.getGraphs()).filter(ProgramMetrics::isMain).findFirst().get();
    toVisit.add(main);
    while (!toVisit.isEmpty()) {
      Graph cur = toVisit.iterator().next();
      toVisit.remove(cur);
      if (reachable.contains(cur)) {
        continue;
      }
      reachable.add(cur);

      toVisit.addAll(graphInfos.get(cur).calls);
    }
    return reachable;
  }

  private static boolean isMain(Graph g) {
    return g.getEntity().getLdName().equals(NameMangler.mangledMainMethodName());
  }

  public static class GraphInfo {
    public final Set<Graph> calls;
    public final boolean diverges;
    public final int size;

    public GraphInfo(Set<Graph> calls, boolean diverges, int size) {
      this.calls = calls;
      this.diverges = diverges;
      this.size = size;
    }
  }

  private static class GraphWalker extends NodeVisitor.Default {
    private final Set<Graph> calls = new HashSet<>();
    private boolean diverges = true;
    private int size = 0;

    @Override
    public void defaultVisit(Node n) {
      size++;
    }

    @Override
    public void visit(End node) {
      // The only possible (optional) predecessors for an end node are a Phi[loop] node at index 0, indicating
      // a possibly diverging loop, and a to-be-kept-alive block at index 1.
      //
      // If a graph certainly diverges, its end node will have both predecessors (possibly more, I don't know).
      diverges = node.getPredCount() >= 2;
      super.visit(node);
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
      super.visit(node);
    }

    public GraphInfo resultSummary() {
      return new GraphInfo(calls, diverges, size);
    }
  }
}
