package minijava.ir.utils;

import com.google.common.base.Objects;
import com.sun.jna.Pointer;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Proj;
import java.util.Comparator;

/**
 * https://en.wikipedia.org/wiki/Extensionality
 *
 * <p>Compares nodes not by their Nr, but by these orderings (in this order of breaking ties):
 *
 * <ul>
 *   <li>{@link Const} nodes are greater than all other nodes
 *   <li>their OpCode
 *   <li>their Mode
 *   <li>their predecessors
 *   <li>Node specific tie breaking (e.g. `TargetValue`s of `Const` nodes)
 * </ul>
 *
 * <p>Nodes which this comparator reports as equal should be exchangeable (modulo dominance)! It's
 * crucial that this doesn't yield false positives.
 */
public class ExtensionalEqualityComparator implements Comparator<Node> {
  public static ExtensionalEqualityComparator INSTANCE = new ExtensionalEqualityComparator();

  @Override
  public int compare(Node o1, Node o2) {
    if (Objects.equal(o1, o2)) {
      return 0;
    }

    Comparator<Node> constNodesLast =
        (a, b) -> {
          if (a.getOpCode().equals(b.getOpCode())) {
            return 0;
          }
          return a.getOpCode().equals(binding_irnode.ir_opcode.iro_Const) ? -1 : 1;
        };

    Comparator<Node> predComparator =
        (a, b) -> {
          int cmp = 0;
          int n = a.getPredCount();
          for (int i = 0; i < n; ++i) {
            if (cmp != 0) {
              return cmp;
            }
            cmp = compare(a.getPred(i), b.getPred(i));
          }
          return cmp;
        };

    Comparator<Node> tieBreakingComparator =
        (a, b) -> {
          TieBreakingCompareVisitor visitor = new TieBreakingCompareVisitor(b);
          a.accept(visitor);
          return visitor.cmp;
        };

    return Comparator.nullsFirst(
            constNodesLast
                .thenComparing(Node::getOpCode)
                .thenComparing(Node::getPredCount)
                .thenComparingLong(n -> Pointer.nativeValue(n.getMode().ptr))
                .thenComparing(predComparator)
                .thenComparing(tieBreakingComparator))
        .compare(o1, o2);
  }

  /**
   * This visitor kicks in if we now that both nodes
   *
   * <p>- are of the same type - have the same mode - have the same pred count - have the same
   * predecessors
   *
   * <p>This is the last possible instance which can reject extensional equality between two nodes.
   * It is crucial that non-equal nodes are detected here!
   */
  private static class TieBreakingCompareVisitor extends NodeVisitor.Default {
    private int cmp = 0;
    private final Node other;

    TieBreakingCompareVisitor(Node other) {
      this.other = other;
    }

    @Override
    public void visit(Const node) {
      TargetValue a = node.getTarval();
      TargetValue b = ((Const) other).getTarval();
      if (a.equals(b)) {
        cmp = 0;
      } else {
        if (node.getMode().equals(Mode.getIs())) {
          cmp = a.asInt() - b.asInt();
        } else if (node.getMode().equals(Mode.getP())) {
          cmp = (int) Math.signum(a.asLong() - b.asLong());
        } else if (node.getMode().equals(Mode.getb())) {
          cmp = a.equals(TargetValue.getBFalse()) ? -1 : 1;
        } else if (node.getMode().equals(Mode.getBu())) {
          cmp = a.asInt() == 0 ? -1 : 1;
        } else {
          assert false;
        }
      }
    }

    @Override
    public void visit(Proj node) {
      cmp = node.getNum() - ((Proj) other).getNum();
    }
  }
}
