package minijava.ir.utils;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.Relation;
import firm.nodes.Node;
import firm.nodes.Phi;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.function.Supplier;
import minijava.ir.assembler.location.Register;

public class FirmUtils {

  public static boolean isPhiProneToLostCopies(Phi phi) {
    // Assumption: a phi is error prone if a circle in the graph exists that
    // contains this phi node
    // we detect a circle by using depth first search
    Set<Integer> visitedNodeIds = new HashSet<>();
    Stack<Node> toVisit = new Stack<>();
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

  public static Register.Width modeToWidth(Mode mode) {
    switch (mode.getSizeBytes()) {
      case 1:
        return Register.Width.Byte;
      case 4:
        return Register.Width.Long;
      case 8:
        return Register.Width.Quad;
    }
    if (mode.isReference()) {
      return Register.Width.Quad;
    } else if (mode.equals(Mode.getb())) {
      return Register.Width.Byte;
    }
    throw new RuntimeException(mode.toString());
  }

  public static String relationToInstructionSuffix(Relation relation) {
    switch (relation) {
      case Greater:
        return "g";
      case GreaterEqual:
        return "ge";
      case Less:
        return "l";
      case LessEqual:
        return "le";
      case Equal:
        return "e";
      case LessGreater:
        return "ne";
      default:
        throw new RuntimeException();
    }
  }
}
