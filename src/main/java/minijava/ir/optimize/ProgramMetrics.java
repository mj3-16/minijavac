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
import java.util.function.Consumer;
import minijava.ir.emit.NameMangler;

public class ProgramMetrics {
  public final Map<Graph, GraphInfo> graphInfos = new HashMap<>();

  private ProgramMetrics() {}

  public static ProgramMetrics analyse(Iterable<Graph> graphs) {
    ProgramMetrics metrics = new ProgramMetrics();
    for (Graph graph : graphs) {
      metrics.updateGraphInfoWithoutLoopBreakers(graph);
    }
    metrics.detectLoopBreakers(graphs);

    return metrics;
  }

  private void detectLoopBreakers(Iterable<Graph> graphs) {
    Map<Graph, Integer> discovered = new HashMap<>();
    Map<Graph, Integer> finished = new HashMap<>();
    Set<Graph> visited = new HashSet<>();
    int[] counter = new int[1];
    counter[0] = 0;
    for (Graph graph : graphs) {
      visitCallGraphInPostorder(
          graph, g -> discovered.put(g, counter[0]++), g -> finished.put(g, counter[0]++), visited);
    }

    // Now we can identify back edges
    for (Graph graph : graphs) {
      GraphInfo info = graphInfos.get(graph);
      for (Graph call : info.calls) {
        // Is this a back edge? It is if it's callee is a parent in the DFS tree.
        boolean isParent =
            discovered.get(call) <= discovered.get(graph)
                && finished.get(call) >= finished.get(graph);
        if (isParent) {
          // This is a back edge. We update the graph info accordingly
          // to mark it as a loop breaker.
          graphInfos.put(graph, info.markedAsLoopBreaker());
        }
      }
    }
  }

  public void updateGraphInfo(Graph graph) {
    updateGraphInfoWithoutLoopBreakers(graph);
  }

  private void updateGraphInfoWithoutLoopBreakers(Graph graph) {
    GraphWalker walker = new GraphWalker();
    graph.walk(walker);
    GraphInfo newInfo = walker.resultSummary();
    GraphInfo oldInfo = graphInfos.get(graph);
    if (oldInfo != null && oldInfo.isLoopBreaker) {
      // We copy the old loop breaker info, so that we don't have to traverse
      // the call graph too often.
      newInfo = newInfo.markedAsLoopBreaker();
    }
    graphInfos.put(graph, newInfo);
  }

  public Set<Graph> reachableFromMain() {
    return reachableFrom(main());
  }

  private static Graph main() {
    return seq(Program.getGraphs()).filter(ProgramMetrics::isMain).findFirst().get();
  }

  private Set<Graph> reachableFrom(Graph graph) {
    Set<Graph> reachable = new HashSet<>();
    Set<Graph> toVisit = new HashSet<>();
    toVisit.add(graph);
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

  private void visitCallGraphInPostorder(
      Graph graph, Consumer<Graph> onDiscovery, Consumer<Graph> onFinish, Set<Graph> visited) {
    if (visited.contains(graph)) {
      return;
    }

    visited.add(graph);
    onDiscovery.accept(graph);
    GraphInfo info = graphInfos.get(graph);

    for (Graph call : info.calls) {
      visitCallGraphInPostorder(call, onDiscovery, onFinish, visited);
    }

    onFinish.accept(graph);
  }

  public static class GraphInfo {
    public final Set<Graph> calls;
    public final boolean diverges;
    public final int size;
    public final boolean isLoopBreaker;

    public GraphInfo(Set<Graph> calls, boolean diverges, int size, boolean isLoopBreaker) {
      this.calls = calls;
      this.diverges = diverges;
      this.size = size;
      this.isLoopBreaker = isLoopBreaker;
    }

    public GraphInfo markedAsLoopBreaker() {
      return new GraphInfo(calls, diverges, size, true);
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
      // When the End block has no predecessors (e.g. Return nodes), the graph definitely diverges.
      diverges = node.getBlock().getPredCount() == 0;
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
      return new GraphInfo(calls, diverges, size, false);
    }
  }
}
