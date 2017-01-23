package minijava.ir.utils;

import static firm.bindings.binding_irnode.ir_opcode.*;
import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.Seq.zipWithIndex;

import com.google.common.collect.ImmutableSet;
import firm.BackEdges;
import firm.bindings.binding_irnode;
import firm.nodes.Block;
import firm.nodes.Cond;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.Proj;
import java.util.Optional;
import java.util.Set;
import org.jooq.lambda.Seq;

/** For lack of a better name */
public class NodeUtils {
  private static final Set<binding_irnode.ir_opcode> SINGLE_EXIT =
      ImmutableSet.of(iro_Jmp, iro_Return);

  public static Optional<Const> asConst(Node node) {
    return node.getOpCode().equals(iro_Const) ? Optional.of((Const) node) : Optional.empty();
  }

  public static Optional<Proj> asProj(Node node) {
    return node.getOpCode().equals(iro_Proj) ? Optional.of((Proj) node) : Optional.empty();
  }

  public static Optional<ProjPair> determineProjectionNodes(Cond node) {
    Proj[] projs =
        seq(BackEdges.getOuts(node)).map(be -> be.node).ofType(Proj.class).toArray(Proj[]::new);

    if (projs.length != 2) {
      return Optional.empty();
    }

    if (projs[0].getNum() == Cond.pnTrue) {
      return Optional.of(new ProjPair(projs[0], projs[1]));
    } else {
      return Optional.of(new ProjPair(projs[1], projs[0]));
    }
  }

  public static Set<Node> getNodesInBlock(Block block) {
    return FirmUtils.withBackEdges(
        block.getGraph(),
        () -> seq(BackEdges.getOuts(block)).filter(be -> be.pos == -1).map(be -> be.node).toSet());
  }

  /** Single exit control flow nodes are Jmp, Return, ..., but not a Proj X. */
  public static boolean isSingleExitNode(Node node) {
    return SINGLE_EXIT.contains(node.getOpCode());
  }

  public static Seq<BackEdges.Edge> criticalEdges(Block block) {
    if (block.getPredCount() < 2) {
      // We are good, target block has at most one incoming edge
      return Seq.empty();
    }

    return zipWithIndex(block.getPreds())
        // This implies that there is only one outgoing control flow edge of the block.
        .filter(t -> !isSingleExitNode(t.v1))
        .map(t -> edge(t.v1, (int) (long) t.v2));
  }

  private static BackEdges.Edge edge(Node node, int pos) {
    BackEdges.Edge e = new BackEdges.Edge();
    e.node = node;
    e.pos = pos;
    return e;
  }
}
