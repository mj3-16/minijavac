package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Minus;
import static minijava.ir.utils.NodeUtils.asConst;

import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import java.util.*;
import minijava.ir.utils.GraphUtils;

/**
 * Performs scalar optimization based on some algebraic identities. Assumes that expressions are
 * normalized, in particular that {@link Const} nodes are always the left argument.
 *
 * <p>For integers, we use the following simplifications:
 *
 * <ul>
 *   <li>Group axioms of +:
 *       <ul>
 *         <li>Identity: 0 + x = x
 *         <li>Associativity: c_1 + (c_2 + x) = (c_1 + c_2) + x
 *         <li>Inverse element: (-x) + x = 0 = x + (-x)
 *         <li>Double inverse: -(-x) = x
 *         <li>Pull down neg: (-x) + (-x) = -(x + x)
 *       </ul>
 *
 *   <li>Monoid axioms of *:
 *       <ul>
 *         <li>Identity: 1 * x = x
 *         <li>Associativity: c_1 * (c_2 * x) = (c_1 * c_2) * x
 *       </ul>
 *
 *   <li>TODO: Integral domain axioms: Distributivity
 * </ul>
 */
public class AlgebraicSimplifier extends BaseOptimizer {

  private static final Group ADD = new AddGroup();
  private static final Monoid MUL = new MulMonoid();

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    hasChanged = false;
    GraphUtils.topologicalOrder(graph).forEach(this::visit);
    return hasChanged;
  }

  @Override
  public void visit(Add node) {
    simplifyBinop(node, ADD);
  }

  @Override
  public void visit(Mul node) {
    simplifyBinop(node, MUL);
  }

  @Override
  public void visit(Minus node) {
    simplifyUnop(node, ADD);
  }

  private void exchange(Node oldNode, Node newNode) {
    hasChanged = true;
    Graph.exchange(oldNode, newNode);
  }

  /** Simplifies the binary operator based on semigroup axioms. */
  private void simplifyBinop(Binop node, Semigroup semigroup) {
    // Only rule we can make use of is associativity. For that we need the left arg to be Const
    // E.g. reassociate c_1 $ (c_2 $ x) = (c_1 $ c_2) $ x and let constant folding do the rest
    Optional<Const> first = asConst(node.getLeft());
    if (first.isPresent() && node.getOpCode().equals(node.getRight().getOpCode())) {
      Binop right = (Binop) node.getRight();
      Optional<Const> second = asConst(right.getLeft());
      if (second.isPresent()) {
        // success :)
        Node newLeft =
            semigroup.makeBinop(graph, (Block) node.getBlock(), first.get(), second.get());
        Node newRight = right.getRight();
        Binop reassociated = semigroup.makeBinop(graph, (Block) node.getBlock(), newLeft, newRight);
        exchange(node, reassociated);
      }
    }
  }

  /** Simplifies the binary operator based on monoid axioms. */
  private void simplifyBinop(Binop node, Monoid monoid) {
    // Other than the semigroup axioms, there is just the isIdentity rule.
    // E.g. id $ x = x = x $ id. Since we assume normalized expressions, we just check the left node.
    Optional<Const> left = asConst(node.getLeft());
    if (left.isPresent() && monoid.isIdentity(left.get())) {
      exchange(node, node.getRight());
    } else {
      simplifyBinop(node, (Semigroup) monoid);
    }
  }

  /** Simplifies the binary operator based on group axioms. */
  private void simplifyBinop(Binop node, Group group) {
    // Other than the monoid axioms, there is just the additional inverse element rule.
    // E.g. x $ inverse(x) = id = inverse(x) $ x. No normalization guarantees here, so we best check both arguments.
    // Also we can pull down inverses: inverse(x) $ inverse(x) = inverse(x $ x)
    Node left = node.getLeft();
    Node right = node.getRight();
    Optional<Node> invLeft = inversedPred(left, group);
    Optional<Node> invRight = inversedPred(right, group);
    boolean exactlyOneInverted = invLeft.isPresent() != invRight.isPresent();
    boolean bothInverted = invLeft.isPresent() && invRight.isPresent();
    if (exactlyOneInverted && invLeft.orElse(left).equals(invRight.orElse(right))) {
      // left and right cancel each other out
      exchange(node, group.makeIdentity(graph, (Block) node.getBlock(), node.getMode()));
    } else if (bothInverted) {
      // we can pull out the inversion
      Binop invNode =
          group.makeBinop(graph, (Block) node.getBlock(), invLeft.get(), invRight.get());
      Node pulledOut = group.makeInversed(graph, (Block) node.getBlock(), invNode);
      exchange(node, pulledOut);
    } else {
      simplifyBinop(node, (Monoid) group);
    }
  }

  /** Simplifies the unary operator based on group axioms. */
  private void simplifyUnop(Node node, Group group) {
    // We can try to remove double inversions, e.g. -(-x) = x
    inversedPred(node, group)
        .flatMap(inv -> inversedPred(inv, group))
        .ifPresent(sameValue -> exchange(node, sameValue));
  }

  private static Optional<Node> inversedPred(Node node, Group group) {
    return group.isInverseNode(node) ? Optional.ofNullable(node.getPred(0)) : Optional.empty();
  }

  // What follows are the encodings of algebraic structures that we make use of.

  private static class AddGroup implements Group {
    @Override
    public Add makeBinop(Graph g, Block b, Node l, Node r) {
      return (Add) g.newAdd(b, l, r);
    }

    @Override
    public boolean isIdentity(Node node) {
      return asConst(node).map(c -> c.getTarval().isNull()).orElse(false);
    }

    @Override
    public Node makeIdentity(Graph graph, Block block, Mode mode) {
      return graph.newConst(new TargetValue(0, mode));
    }

    @Override
    public boolean isInverseNode(Node node) {
      return node.getOpCode().equals(iro_Minus);
    }

    @Override
    public Node makeInversed(Graph graph, Block block, Node toInvert) {
      return graph.newMinus(block, toInvert);
    }
  }

  private static class MulMonoid implements Monoid {
    @Override
    public Mul makeBinop(Graph g, Block b, Node l, Node r) {
      return (Mul) g.newMul(b, l, r);
    }

    @Override
    public boolean isIdentity(Node node) {
      return asConst(node).map(c -> c.getTarval().isOne()).orElse(false);
    }

    @Override
    public Node makeIdentity(Graph graph, Block block, Mode mode) {
      return graph.newConst(new TargetValue(1, mode));
    }
  }

  /** Binop has to be associative */
  private interface Semigroup {
    Binop makeBinop(Graph graph, Block block, Node left, Node right);
  }

  /** isIdentity(node) checks if node is an identity element to the Binop */
  private interface Monoid extends Semigroup {
    boolean isIdentity(Node node);

    Node makeIdentity(Graph graph, Block block, Mode mode);
  }

  /** binop(a, invert(a)) = isIdentity */
  private interface Group extends Monoid {
    boolean isInverseNode(Node node);

    Node makeInversed(Graph graph, Block block, Node toInvert);
  }
}
