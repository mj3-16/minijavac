package minijava.ir.assembler;

import com.google.common.collect.Lists;
import firm.Graph;
import firm.nodes.Block;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.Dominance;
import minijava.ir.utils.NodeUtils;

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

  private static void insert(Node tree, Block block) {
    assert Dominance.dominates(tree.block, block);

    Node dominator = dominatingNode(tree.children, block);
    while (dominator != null) {
      tree = dominator;
      dominator = dominatingNode(tree.children, block);
    }

    // Node will be a new child. Also check if we dominate any other child and reassign them.
    Node node = new Node(block);
    for (Node dominated : dominatedNodes(tree.children, node.block)) {
      tree.children.remove(dominated);
      node.children.add(dominated);
    }
    addChildPlacingLoopBodiesBeforeExits(tree, node);
  }

  private static void addChildPlacingLoopBodiesBeforeExits(Node parent, Node node) {
    if (reachableFromABackEdge(parent.block, node.block)) {
      // The node is part of the loop body, so insert it at the front
      parent.children.add(0, node);
    } else {
      // There is no path back to the parent, so this is a loop exit (if it was a loop)
      // Adding it to the back is preferable.
      parent.children.add(node);
    }
  }

  private static boolean reachableFromABackEdge(Block header, Block block) {
    for (int predNum : NodeUtils.incomingBackEdges(header)) {
      Block footer = (Block) header.getPred(predNum).getBlock();
      if (isTransitivePred(footer, block)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isTransitivePred(Block source, Block target) {
    ArrayDeque<Block> toVisit = new ArrayDeque<>();
    Set<Block> visited = new HashSet<>();
    toVisit.add(source);
    while (!toVisit.isEmpty()) {
      Block cur = toVisit.removeFirst();
      if (visited.contains(cur)) {
        continue;
      }
      visited.add(cur);

      if (cur.equals(target)) {
        return true;
      }

      NodeUtils.getPredecessorBlocks(cur).forEach(toVisit::add);
    }
    return false;
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
    for (Node node : nodes) {
      if (Dominance.dominates(node.block, block)) {
        return node;
      }
    }
    return null;
  }

  public static class Node {
    public final Block block;
    public final List<Node> children = new ArrayList<>();

    public Node(Block block) {
      this.block = block;
    }
  }
}
