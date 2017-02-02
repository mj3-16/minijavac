package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Anchor;
import static firm.bindings.binding_irnode.ir_opcode.iro_Phi;
import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Block;
import firm.nodes.Node;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.Dominance;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import org.jooq.lambda.tuple.Tuple2;

/**
 * Floats definitions as near as possible to their uses, to lower register pressure and enable
 * common subexpression elimination to kick in once more.
 */
public class FloatInTransformation extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    // Not sure if we really need more than one pass here, but better be safe.
    return fixedPointIteration(GraphUtils.reverseTopologicalOrder(graph));
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

    List<List<Block>> reverseDominatorPaths =
        seq(uses)
            .map(
                use ->
                    Dominance.dominatorPath(use)
                        // We'll hit the original block at one point, which we exclude!
                        .limitUntil(b -> b.equals(originalBlock))
                        .reverse()
                        .toList())
            .toList();

    if (reverseDominatorPaths.isEmpty()) {
      // This is somewhat unlikely, as it would not be reached in the graph walk.
      // We can't handle this in any useful manner.
      return;
    }

    // We now compute the longest common dominator path. This is easiest done in reverse, by just
    // intersecting all paths.
    List<Block> reverseCommonDominatorPath = intersectPaths(reverseDominatorPaths);

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

  private static List<Block> intersectPaths(List<List<Block>> paths) {
    assert !paths.isEmpty();

    List<Block> ret = paths.get(0);
    for (int i = 1; i < paths.size(); ++i) {
      ret = seq(ret).zip(paths.get(i)).limitWhile(t -> t.v1.equals(t.v2)).map(Tuple2::v1).toList();
    }
    return ret;
  }

  private static Set<Block> findUses(Node node) {
    return FirmUtils.withBackEdges(
        node.getGraph(),
        () -> {
          Set<Block> ret = new HashSet<>();
          for (BackEdges.Edge be : BackEdges.getOuts(node)) {
            Node n = be.node;
            if (n.getOpCode().equals(iro_Anchor)) {
              // ... whatever
              continue;
            }

            if (n.getBlock() == null) {
              ret.add((Block) n);
              continue;
            }

            if (n.getOpCode().equals(iro_Phi)) {
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
