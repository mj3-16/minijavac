package minijava.ir.utils;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Address;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.Phi;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;

public class FirmUtils {

  public static String getMethodLdName(firm.nodes.Call node) {
    return ((Address) node.getPred(1)).getEntity().getLdName();
  }

  public static boolean isPhiProneToLostCopies(Phi phi) {
    // Assumption: a phi is error prone if a circle in the graph exists that
    // contains this phi node
    // we detect a circle by using depth first search
    Block phiBlock = (Block) phi.getBlock();
    Set<Integer> visitedNodeIds = new HashSet<>();
    Stack<Node> toVisit = new Stack<Node>();
    toVisit.add(phi);
    while (!toVisit.isEmpty()) {
      Node currentNode = toVisit.pop();
      visitedNodeIds.add(currentNode.getNr());
      for (Node node : currentNode.getPreds()) {
        if (node.equals(phi)) {
          return true;
        }
        if (!visitedNodeIds.contains(node.getNr())) {
          toVisit.push(node);
        }
      }
    }
    return false;
  }

  public static <T> T withBackEdges(Graph graph, Supplier<T> body) {
    boolean responsible = !BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.enable(graph);
    }
    try {
      return body.get();
    } finally {
      if (responsible) {
        BackEdges.disable(graph);
      }
    }
  }

  public static void withBackEdges(Graph graph, Runnable body) {
    boolean responsible = !BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.enable(graph);
    }
    try {
      body.run();
    } finally {
      if (responsible) {
        BackEdges.disable(graph);
      }
    }
  }

  public static <T> T withoutBackEdges(Graph graph, Supplier<T> body) {
    boolean responsible = BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.disable(graph);
    }
    try {
      return body.get();
    } finally {
      if (responsible) {
        BackEdges.enable(graph);
      }
    }
  }

  public static void withoutBackEdges(Graph graph, Runnable body) {
    boolean responsible = BackEdges.enabled(graph);
    if (responsible) {
      BackEdges.disable(graph);
    }
    try {
      body.run();
    } finally {
      if (responsible) {
        BackEdges.enable(graph);
      }
    }
  }
}
