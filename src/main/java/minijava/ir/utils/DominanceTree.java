package minijava.ir.utils;

import com.google.common.collect.Lists;
import firm.Graph;
import firm.nodes.Block;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DominanceTree {
  public final Node root;

  public DominanceTree(Node root) {
    this.root = root;
  }

  /**
   * A preorder traversal of the tree is a good candidate for a linearization of blocks for Linear
   * Scan. Loop bodies will come before loop exits, which is also a good order for code gen.
   */
  public List<Block> preorder() {
    ArrayList<Block> ret = new ArrayList<>();
    ArrayDeque<Node> toVisit = new ArrayDeque<>();
    toVisit.add(root);
    while (!toVisit.isEmpty()) {
      Node cur = toVisit.removeLast();
      ret.add(cur.block);
      toVisit.addAll(Lists.reverse(cur.children));
    }
    return ret;
  }

  public static DominanceTree ofBlocks(Iterable<Block> blocks) {
    Node root = null;
    Graph graph = null;
    for (Block block : blocks) {
      // Some house keeping for the first iteration
      if (graph == null) {
        graph = block.getGraph();
      } else {
        // Dominance trees beyond graph borders aren't trees and don't make sense.
        assert graph.equals(block.getGraph());
      }

      if (root == null) {
        root = new Node(graph.getStartBlock());
      }

      if (block.equals(graph.getStartBlock())) {
        // We already assumed this node as the root of the tree.
        // Ignoring this as we would add the block twice.
        continue;
      }

      insert(root, block);
    }
    return new DominanceTree(root);
  }

  private static void insert(Node parent, Block block) {
    assert Dominance.dominates(parent.block, block);

    Node dominator = dominatingNode(parent.children, block);
    while (dominator != null) {
      parent = dominator;
      dominator = dominatingNode(parent.children, block);
    }

    // Node will be a new child. Also check if we dominate any other child and reassign them.
    Node newChild = new Node(block);
    for (Node dominated : dominatedNodes(parent.children, newChild.block)) {
      parent.children.remove(dominated);
      newChild.children.add(dominated);
    }
    parent.children.add(newChild);
  }

  private static List<Node> dominatedNodes(List<Node> nodes, Block block) {
    List<Node> dominated = new ArrayList<>();
    for (Node node : nodes) {
      if (Dominance.dominates(block, node.block)) {
        dominated.add(node);
      }
    }
    return dominated;
  }

  private static Node dominatingNode(List<Node> nodes, Block block) {
    Node candidate = null;
    for (Node node : nodes) {
      if (Dominance.dominates(node.block, block)) {
        assert candidate == null : "The dominator should be unique, if present";
        candidate = node;
      }
    }
    return candidate;
  }

  /**
   * Imposes an order among children of the same direct dominator. Useful for first ordering for
   * dominance, then after a topological sort.
   */
  public void sortChildren(Comparator<Node> comparator) {
    ArrayDeque<Node> toVisit = new ArrayDeque<>();
    toVisit.add(root);
    while (!toVisit.isEmpty()) {
      Node cur = toVisit.removeFirst();
      cur.children.sort(comparator);
      toVisit.addAll(cur.children);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    prettyPrintSExpr(root, builder);
    return builder.toString();
  }

  private void prettyPrintSExpr(Node parent, StringBuilder builder) {
    builder.append('(');
    builder.append(parent.block.getNr());
    for (Node child : parent.children) {
      builder.append(' ');
      prettyPrintSExpr(child, builder);
    }
    builder.append(')');
  }

  public static class Node {
    public final Block block;
    public final List<Node> children = new ArrayList<>();

    public Node(Block block) {
      this.block = block;
    }
  }
}
