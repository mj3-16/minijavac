package minijava.ir.utils;

import static org.jooq.lambda.tuple.Tuple.tuple;

import firm.Graph;
import firm.nodes.End;
import firm.nodes.Node;
import firm.nodes.Start;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jooq.lambda.tuple.Tuple2;

public class GraphUtils {

  /**
   * Copies all nodes from graph {@code from} into the graph {@code to}, reconstructs the edges
   * between the copied nodes and returns the copied Start node and the copied End node.
   */
  public static Tuple2<Start, End> copyGraph(Graph from, Graph to) {
    CopyWorker worker = new CopyWorker(to);
    return FirmUtils.withoutBackEdges(
        to, () -> tuple(worker.copyNode(from.getStart()), worker.copyNode(from.getEnd())));
  }

  private static class CopyWorker {
    private final Map<Node, Node> mapping = new HashMap<>();
    private final Graph graph;

    CopyWorker(Graph graph) {
      this.graph = graph;
    }

    private <T extends Node> T copyNode(T node) {
      if (mapping.containsKey(node)) {
        return (T) mapping.get(node);
      }
      T copy = (T) graph.copyNode(node);
      mapping.put(node, copy);
      mapping.put(copy, copy); // Just in case some reference was already updated
      if (node.getBlock() != null) {
        copy.setBlock(copyNode(copy.getBlock()));
      }
      final int n = copy.getPredCount();
      for (int i = 0; i < n; ++i) {
        Node pred = copy.getPred(i);
        copy.setPred(i, copyNode(pred));
      }
      return copy;
    }
  }

  public static boolean areConnected(Node source, Node target) {
    final boolean[] connected = {false};
    Consumer<Node> visitor =
        n -> {
          if (n.equals(target)) {
            connected[0] = true;
          }
        };

    walkFromNodeDepthFirst(source, visitor, n -> {});

    return connected[0];
  }

  /**
   * Computes a topological sorting on the predecessor (!) graph. This is the right traversal for
   * things that change control flow. You can assume (except for back edges) that the nodes are
   * untouched when visited, e.g. predecessors are exactly the same as when the walk was started.
   */
  public static void walkReversePostOrder(Graph graph, Consumer<Node> visitNode) {
    ArrayDeque<Node> stack = new ArrayDeque<>();
    walkPostOrder(graph, stack::addFirst);
    stack.forEach(visitNode);
  }

  public static void walkPostOrder(Graph graph, Consumer<Node> visitNode) {
    walkFromNodeDepthFirst(graph.getEnd(), n -> {}, visitNode);
  }

  public static void walkPreOrder(Graph graph, Consumer<Node> visitNode) {
    walkFromNodeDepthFirst(graph.getEnd(), visitNode, n -> {});
  }

  /**
   * Walks all graph nodes reachable via predecessor edges from {@param seed} and calls {@param
   * onDiscover} and {@param onFinish} in preorder resp. postorder.
   *
   * @param seed Seed of the depth-first traversal
   * @param onDiscover Called with reachable nodes in preorder
   * @param onFinish Called with reachable nodes in postorder
   */
  public static void walkFromNodeDepthFirst(
      Node seed, Consumer<Node> onDiscover, Consumer<Node> onFinish) {
    Deque<Tuple2<Node, Integer>> greyStack = new ArrayDeque<>();
    Set<Node> discovered = new HashSet<>();
    greyStack.addFirst(tuple(seed, -1));
    while (!greyStack.isEmpty()) {
      Tuple2<Node, Integer> nextGrey = greyStack.removeFirst();
      Node node = nextGrey.v1;
      int counter = nextGrey.v2;

      if (counter < 0) {
        // we haven't yet discovered this node
        discovered.add(node);
        onDiscover.accept(node);
        // next time only visit preds
        greyStack.addFirst(tuple(node, 0));

        if (node.getBlock() != null && !discovered.contains(node.getBlock())) {
          greyStack.addFirst(tuple(node.getBlock(), -1));
        }
      } else if (counter < node.getPredCount()) {
        // we have to visit all children first
        greyStack.addFirst(tuple(node, counter + 1));
        if (!discovered.contains(node.getPred(counter))) {
          greyStack.addFirst(tuple(node.getPred(counter), -1));
        }
      } else {
        // All children were visited! we can finish this node
        onFinish.accept(node);
      }
    }
  }
}
