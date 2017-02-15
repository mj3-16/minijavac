package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.utils.Dominance;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;

/**
 * Floats definitions as near as possible to their uses, to lower register pressure and enable
 * common subexpression elimination to kick in once more.
 */
public class FloatInTransformation extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    // Not sure if we really need more than one pass here, but better be safe.
    boolean hasChanged = fixedPointIteration(GraphUtils.reverseTopologicalOrder(graph));
    moveProjsToPred(GraphUtils.topologicalOrder(graph));
    return hasChanged;
  }

  private void moveProjsToPred(ArrayList<Node> order) {
    for (Node proj : order) {
      if (!(proj instanceof Proj)) {
        continue;
      }

      proj.setBlock(proj.getPred(0).getBlock());
    }
  }

  @Override
  public void defaultVisit(Node n) {
    if (NodeUtils.isTiedToBlock(n)) {
      // We can't move anything if it's tied to its block.
      return;
    }

    floatIn(n);
  }

  private void floatIn(Node node) {
    Block originalBlock = (Block) node.getBlock();

    Set<Block> uses = findUses(node);

    // We now compute the longest common dominator path. This is easiest done in reverse, by just
    // intersecting all paths.
    Block deepestCommonDom = seq(uses).reduce(Dominance::deepestCommonDominator).get();
    List<Block> reverseCommonDominatorPath =
        Dominance.dominatorPath(deepestCommonDom)
            .limitUntil(b -> b.equals(originalBlock))
            .reverse()
            .toList();

    // All these dominators are candidates where to move the block. We have to be careful that
    // we don't move stuff into a loop, though! That's why we take the 'most immediate' dominator
    // that has no incoming back edge.
    // As we move along reverseCommonDominatorPath, the dominators get 'more immediate'.
    // This means we just return our last best block when we hit a back edge.
    Block candidate = originalBlock;
    for (Block b : reverseCommonDominatorPath) {
      if (NodeUtils.hasIncomingBackEdge(b)) {
        break;
      }
      candidate = b;
    }

    if (!candidate.equals(originalBlock)) {
      hasChanged = true;
      node.setBlock(candidate);
    }
  }

  private static Set<Block> findUses(Node node) {
    return FirmUtils.withBackEdges(
        node.getGraph(),
        () -> {
          Set<Block> ret = new HashSet<>();
          for (BackEdges.Edge be : BackEdges.getOuts(node)) {
            Node n = be.node;
            if (n instanceof Anchor) {
              // ... whatever
              continue;
            }

            if (n.getBlock() == null) {
              ret.add((Block) n);
              continue;
            }

            if (n instanceof Phi) {
              // This is a tricky one: we use the referenced predecessor as the use
              // (like it would be lowered)
              n = n.getBlock().getPred(be.pos);
            }

            ret.add((Block) n.getBlock());
          }
          return ret;
        });
  }
}
