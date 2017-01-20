package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.Mode;
import firm.nodes.*;
import java.util.*;
import minijava.ir.Dominance;
import minijava.ir.utils.ExtensionalEqualityComparator;
import minijava.ir.utils.GraphUtils;
import org.jooq.lambda.Seq;

/**
 * Implements Common Subexpression Elimination by hashing node specific values and their
 * predecessors. Works best if the predecessor order of commutative/(anti-)symmetric operators was
 * normalized before.
 *
 * <p>There's one subtlety: Beyond BasicBlock boundaries, we have to take dominance into account. So
 * among all similar subtrees, we have to determine which node is dominated by which.
 *
 * <p>This produces a Forest of dominance trees (modulo nodes in the same block) in UnionFind-style
 * fashion. With that information in place, we can substitute all similar nodes by their dominance
 * root after path compression (yielding a forest of star graphs).
 */
public class CommonSubexpressionElimination extends BaseOptimizer {

  /**
   * We cache HashedNode instances for Nodes we already encountered. This also breaks cycles in the
   * analysis.
   */
  private final Map<Node, HashedNode> hashes = new HashMap<>();
  /**
   * Maps HashedNodes to all Nodes that are similar, e.g. can be substituted to the same expression
   * (disregarding block boundaries).
   *
   * <p>So, if we just hashed Node a to a HashedNode instance ha, we can look up ha in this map to
   * see if their are any other Nodes b computing the same expression.
   */
  private final Map<HashedNode, Set<Node>> similarNodes = new HashMap<>();

  @Override
  public boolean optimize(Graph graph) {
    this.hashes.clear();
    this.similarNodes.clear();
    // One postorder traversal should be enough, as we don't handle Phi nodes and thus cycles.
    GraphUtils.postOrder(graph).forEach(this::visit);
    return transform();
  }

  /**
   * This performs the actual substitution after the analysis. As noted in the class javadoc, we
   * have to take dominance into account when substituting.
   */
  private boolean transform() {
    boolean hasChanged = false;
    for (Map.Entry<HashedNode, Set<Node>> entry : similarNodes.entrySet()) {
      if (entry.getValue().size() > 1) {
        // There were similar nodes. Let's see if dominance allows us to combine them
        Map<Node, Node> parents = findDominanceRoots(entry.getValue());
        // The map now contains the edge relation of the forest of star graphs. Roots point to themselves.
        for (Map.Entry<Node, Node> edge : parents.entrySet()) {
          boolean hasNoDominator = edge.getKey().equals(edge.getValue());
          if (hasNoDominator) {
            // This node has no dominator, we have to keep it.
            continue;
          }
          // otherwise we can exchange it for a reference to a dominated expression
          Graph.exchange(edge.getKey(), edge.getValue());
          hasChanged = true;
        }
      }
    }
    return hasChanged;
  }

  /** This computes the dominance forest of star graphs in a UnionFind-like manner. */
  private Map<Node, Node> findDominanceRoots(Iterable<Node> nodes) {
    Map<Node, Node> roots = new HashMap<>();
    for (Node node : nodes) {
      Block block = (Block) node.getBlock();
      Optional<Node> dominator =
          seq(roots.keySet())
              .filter(n -> Dominance.dominates((Block) n.getBlock(), block))
              .map(n -> compressAndReturnRoot(n, roots))
              .findAny();

      if (dominator.isPresent()) {
        roots.put(node, dominator.get());
        continue;
      }

      // Found a new dominance root
      roots.put(node, node);

      // Now find other dominated children. This will also compress paths, since this preserves the invariant
      // that the dominance root of each node is the direct parent (node is the root and the following
      // rearranges edges accordingly).
      for (Map.Entry<Node, Node> edge : roots.entrySet()) {
        if (Dominance.dominates(block, (Block) edge.getKey().getBlock())) {
          edge.setValue(node);
        }
      }
    }

    assert isForestOfStarGraphs(roots);

    return roots;
  }

  /** Every node in a star graph have a central root node which is their direct parent. */
  private static boolean isForestOfStarGraphs(Map<Node, Node> roots) {
    for (Map.Entry<Node, Node> edge : roots.entrySet()) {
      Node parent = edge.getValue();
      boolean parentIsRoot = parent.equals(roots.get(parent));
      if (!parentIsRoot) {
        return false;
      }
    }
    return true;
  }

  /**
   * We follow the parent relation until we find a root (e.g. where the parent is the node itself),
   * which we return. Also we update the parents relation as we go, so that it always points to the
   * root (path compression).
   */
  private static Node compressAndReturnRoot(Node node, Map<Node, Node> parents) {
    Node parent = parents.get(node);
    boolean isRoot = parent.equals(node);
    if (isRoot) {
      return node;
    }
    Node root = compressAndReturnRoot(parent, parents);
    if (!parent.equals(root)) {
      parents.put(node, root);
    }
    return root;
  }

  @Override
  public void visit(Add node) {
    binaryNode(node);
  }

  @Override
  public void visit(Address node) {
    int hash = node.getOpCode().hashCode() ^ node.getEntity().hashCode();
    hashes.put(node, new HashedNode(node, hash));
    // As with Const nodes, we don't add an entry to similarNodes.
  }

  @Override
  public void visit(And node) {
    binaryNode(node);
  }

  @Override
  public void visit(Call node) {
    naryNode(node);
  }

  @Override
  public void visit(Cmp node) {
    binaryNode(node);
  }

  @Override
  public void visit(Cond node) {
    // We may not eliminate control flow
  }

  @Override
  public void visit(Const node) {
    int hash = node.getOpCode().hashCode() ^ node.getTarval().hashCode();
    hashes.put(node, new HashedNode(node, hash));
    // We don't add an entry to similarNodes, because consts are cheap to duplicate
    // and shouldn't block registers when shared.
    // Also it seems that shared Consts/Address nodes are duplicated by firm, so this won't work.
  }

  @Override
  public void visit(Conv node) {
    unaryNode(node);
  }

  @Override
  public void visit(Div node) {
    binaryNode(node);
  }

  @Override
  public void visit(Minus node) {
    unaryNode(node);
  }

  @Override
  public void visit(Mod node) {
    binaryNode(node);
  }

  @Override
  public void visit(Mul node) {
    binaryNode(node);
  }

  @Override
  public void visit(Not node) {
    unaryNode(node);
  }

  @Override
  public void visit(Or node) {
    binaryNode(node);
  }

  @Override
  public void visit(Phi node) {
    // Don't handle these at all for now, because of back edges and other nastiness.
  }

  @Override
  public void visit(Proj node) {
    if (node.getMode().equals(Mode.getX())) {
      // We may not eliminate control flow
      return;
    }
    hashWithSalt(node.getOpCode().hashCode() ^ node.getNum(), node.getPred(0))
        .ifPresent(hash -> updateHashMapping(node, hash));
  }

  @Override
  public void visit(Shl node) {
    binaryNode(node);
  }

  @Override
  public void visit(Shr node) {
    binaryNode(node);
  }

  @Override
  public void visit(Shrs node) {
    binaryNode(node);
  }

  @Override
  public void visit(Sub node) {
    binaryNode(node);
  }

  @Override
  public void visit(Start node) {
    hashWithSalt(Start.class.hashCode()).ifPresent(hash -> updateHashMapping(node, hash));
  }

  /**
   * Helper method called for all uninteresting (e.g. no relevant discerning state beyond their node
   * type and preds) unary nodes.
   */
  private void unaryNode(Node node) {
    hashWithSalt(node.getOpCode().hashCode(), node.getPred(0))
        .ifPresent(hash -> updateHashMapping(node, hash));
  }

  /**
   * Helper method called for all uninteresting (e.g. no relevant discerning state beyond their node
   * type and preds) binary nodes.
   */
  private void binaryNode(Node node) {
    hashWithSalt(node.getOpCode().hashCode(), node.getPred(0), node.getPred(1))
        .ifPresent(hash -> updateHashMapping(node, hash));
  }

  /** Exclusively used for call instructions, but could also be used for e.g. Tuples. */
  private void naryNode(Node node) {
    Node[] preds = seq(node.getPreds()).toArray(Node[]::new);
    hashWithSalt(node.getOpCode().hashCode(), preds);
  }

  /**
   * Updates the bidirectional mapping 'node -> hashednode' and 'hashednode -> set of similar
   * nodes'.
   */
  private void updateHashMapping(Node node, int hash) {
    HashedNode hashed = new HashedNode(node, hash);
    addHashedNode(hashed);
    hashes.put(node, hashed);
  }

  private void addHashedNode(HashedNode hashed) {
    Set<Node> similar =
        similarNodes.containsKey(hashed) ? similarNodes.get(hashed) : new HashSet<>();
    similar.add(hashed.node);
    similarNodes.put(hashed, similar);
  }

  /**
   * Hashes a number of nodes, starting from a salt hash. If the hashes of the predecessors haven't
   * been computed yet (or can't be, e.g. Phis) then we return Optional.empty().
   */
  private Optional<Integer> hashWithSalt(int salt, Node... preds) {
    // Java really needs `mapM`. /jk
    Object[] hashedPreds = Seq.of(preds).map(hashes::get).toArray(Object[]::new);
    if (Seq.of(hashedPreds).contains(null)) {
      return Optional.empty();
    }
    return Optional.of(salt ^ Objects.hash(hashedPreds));
  }

  /**
   * This better be private! This wraps around a node to produce an own hashCode() and equals()
   * implementation conflating nodes which compute the same expressions.
   */
  private static class HashedNode {
    public final Node node;
    final int hash;

    private HashedNode(Node node, int hash) {
      this.node = node;
      this.hash = hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof HashedNode)) {
        return false;
      }
      return ExtensionalEqualityComparator.INSTANCE.compare(this.node, ((HashedNode) obj).node)
          == 0;
    }

    @Override
    public int hashCode() {
      return hash;
    }
  }
}
