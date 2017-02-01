package minijava.ir.utils;

import com.google.common.base.Objects;
import com.sun.jna.Pointer;
import firm.ArrayType;
import firm.Mode;
import firm.TargetValue;
import firm.bindings.binding_irnode;
import firm.bindings.binding_irnode.ir_opcode;
import firm.nodes.Cmp;
import firm.nodes.Const;
import firm.nodes.Load;
import firm.nodes.Member;
import firm.nodes.Node;
import firm.nodes.NodeVisitor;
import firm.nodes.Phi;
import firm.nodes.Proj;
import firm.nodes.Sel;
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
          if (a.getOpCode() == ir_opcode.iro_Phi) {
            // We have to break loops here before we may access the preds.
            return a.getNr() - b.getNr();
          }
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
   * <ul>
   *   <li>are of the same type
   *   <li>have the same mode
   *   <li>have the same pred count
   *   <li>have the same predecessors
   * </ul>
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
      if (node.getMode().equals(Mode.getb())) {
        cmp = a.equals(TargetValue.getBFalse()) ? -1 : 1;
      } else {
        // We can't easily get p64 mode, so being optimistic here is the best thing to do
        cmp = (int) Math.signum(a.asLong() - b.asLong());
      }
    }

    @Override
    public void visit(Sel node) {
      cmp = selectedElementSize(node) - selectedElementSize((Sel) other);
    }

    private int selectedElementSize(Sel node) {
      ArrayType arrayType = (ArrayType) node.getType();
      return arrayType.getElementType().getSize();
    }

    @Override
    public void visit(Member node) {
      cmp = node.getEntity().getOffset() - ((Member) other).getEntity().getOffset();
    }

    @Override
    public void visit(Cmp node) {
      Cmp otherCmp = (Cmp) other;
      cmp = node.getRelation().compareTo(otherCmp.getRelation());
    }

    @Override
    public void visit(Proj node) {
      // Two different projs should never be considered equal.
      cmp = node.getNr() - other.getNr();
    }

    @Override
    public void visit(Load node) {
      Mode otherLoadMode = ((Load) other).getLoadMode();
      if (node.getLoadMode().equals(otherLoadMode)) {
        return;
      }
      cmp = node.getNr() - other.getNr();
    }

    @Override
    public void visit(Phi node) {
      cmp = node.getNr() - other.getNr();
    }
  }
}
