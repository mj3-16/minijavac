package minijava.ir.utils;

import static firm.bindings.binding_irnode.ir_opcode.iro_Jmp;
import static firm.bindings.binding_irnode.ir_opcode.iro_Proj;
import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.Seq.zipWithIndex;

import com.sun.jna.Pointer;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import minijava.ir.Dominance;
import org.jooq.lambda.Seq;

/** For lack of a better name */
public class NodeUtils {
  public static Optional<Const> asConst(Node node) {
    return node instanceof Const ? Optional.of((Const) node) : Optional.empty();
  }

  public static Optional<Proj> asProj(Node node) {
    return node instanceof Proj ? Optional.of((Proj) node) : Optional.empty();
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
    return seq(BackEdges.getOuts(block)).filter(be -> be.pos == -1).map(be -> be.node).toSet();
  }

  /** Single exit control flow nodes are Jmp, Return, ..., but not a Proj X. */
  public static boolean isSingleExitNode(Node node) {
    return node instanceof Jmp || node instanceof Return;
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

  /** Splits a critical edge beginning at source, if necessary, and returns the new block, if so. */
  public static Optional<Block> splitCriticalEdge(Block source, int pos) {
    Node target = source.getPred(pos);
    if (target.equals(iro_Jmp)) {
      return Optional.empty();
    }
    Graph graph = source.getGraph();
    Block splitter = (Block) graph.newBlock(new Node[] {target});
    source.setPred(pos, graph.newJmp(splitter));
    Dominance.invalidateDominace();
    return Optional.of(splitter);
  }

  private static BackEdges.Edge edge(Node node, int pos) {
    BackEdges.Edge e = new BackEdges.Edge();
    e.node = node;
    e.pos = pos;
    return e;
  }

  /**
   * There may only ever be one Proj per Num (e.g. M, Res, True, False, etc.). This helper will go
   * through all reverse preds of every Proj with Num num and point them to one desiganted survivor
   * proj.
   *
   * @param node The node where there might be duplicate projs
   * @param num The num of the Projs to merge.
   */
  public static void mergeProjsWithNum(Node node, int num) {
    List<Proj> projs = new ArrayList<>();
    for (BackEdges.Edge be : BackEdges.getOuts(node)) {
      asProj(be.node)
          .ifPresent(
              proj -> {
                if (proj.getNum() == num) {
                  projs.add(proj);
                }
              });
    }
    // this will do the right thing in case there aren't any projs as well as when there are 1 or more.
    for (Proj proj : seq(projs).skip(1)) {
      Proj survivor = projs.get(0);
      for (BackEdges.Edge be : BackEdges.getOuts(proj)) {
        be.node.setPred(be.pos, survivor);
        if (be.node instanceof Block) {
          Dominance.invalidateDominace();
        }
      }
      Graph.killNode(proj);
    }
  }

  /**
   * This will point all Projs of {@param oldTarget} to {@param newTarget} and after that will merge
   * Projs with the same Num (because there may only be a single proj for Num).
   */
  public static void redirectProjsOnto(Node oldTarget, Node newTarget) {
    Set<Integer> usedNums = new HashSet<>();

    for (BackEdges.Edge be : BackEdges.getOuts(oldTarget)) {
      // Point the projs to newTarget
      if (!(be.node instanceof Proj)) {
        continue;
      }
      be.node.setPred(be.pos, newTarget);
      be.node.setBlock(newTarget.getBlock());
      usedNums.add(((Proj) be.node).getNum());
    }

    usedNums.forEach(num -> mergeProjsWithNum(newTarget, num));
  }

  /** Make sure to have called GraphUtils.reserveResource(graph, IR_RESOURCE_IRN_LINK) before! */
  public static void setLink(Node node, Pointer val) {
    binding_irnode.set_irn_link(node.ptr, val);
  }

  public static Pointer getLink(Node node) {
    return binding_irnode.get_irn_link(node.ptr);
  }

  /** Projects out the Mem effect of the passed side effect if necessary. */
  public static Node projModeMOf(Node sideEffect) {
    if (sideEffect.getMode().equals(Mode.getM())) {
      return sideEffect;
    }
    return getMemProjSuccessor(sideEffect);
  }

  public static boolean hasIncomingBackEdge(Block b) {
    return incomingBackEdges(b).isNotEmpty();
  }

  public static Seq<Integer> incomingBackEdges(Block b) {
    // A back edge (not to confuse with jFirms notion of BackEdges,
    // which is misnomer for reverse edges) is an edge where the source's block
    // is dominated by the target.
    return seq(b.getPreds())
        .zipWithIndex()
        .filter(pred -> Dominance.dominates(b, (Block) pred.v1.getBlock()))
        .map(pred -> (int) (long) pred.v2);
  }

  public static boolean isTiedToBlock(Node node) {
    switch (node.getOpCode()) {
      case iro_Add:
      case iro_Sub:
      case iro_Mul:
      case iro_Div:
      case iro_Mod:
      case iro_Minus:
      case iro_Or:
      case iro_And:
      case iro_Not:
      case iro_Shl:
      case iro_Shr:
      case iro_Shrs:
      case iro_Load:
      case iro_Store:
      case iro_Conv:
      case iro_Call:
      case iro_Cmp:
      case iro_Mux:
      case iro_Member:
      case iro_Sel:
      case iro_Sync:
      case iro_Tuple:
        return false;
      case iro_Proj:
        // Should be true for control flow (in which case a Cond is pred) and for cases like
        // Proj on Start. Besides, nothing is gained by moving a Proj without it's pred anyway.
        return isTiedToBlock(node.getPred(0));
      default:
        return true;
    }
  }

  /** Returns the unique Proj of mode M depending on {@param sideEffect} or creates one. */
  public static Proj getMemProjSuccessor(Node sideEffect) {
    return seq(BackEdges.getOuts(sideEffect))
        .map(be -> be.node)
        .ofType(Proj.class)
        .filter(p -> p.getMode().equals(Mode.getM()))
        .findFirst()
        .orElseGet(() -> (Proj) sideEffect.getGraph().newProj(sideEffect, Mode.getM(), Load.pnM));
  }

  /** This returns reverse edges to successor blocks. */
  public static Seq<BackEdges.Edge> getControlFlowSuccessors(Block block) {
    return seq(BackEdges.getOuts(block))
        .map(be -> be.node)
        .filter(n -> n.getMode().equals(Mode.getX()))
        .map(BackEdges::getOuts)
        .flatMap(Seq::seq)
        .filter(be -> be.node instanceof Block);
  }

  public static Seq<Block> getPredecessorBlocks(Block cur) {
    return seq(cur.getPreds()).map(Node::getBlock).cast(Block.class);
  }
}
