package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.nodes.Block;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;

/**
 * Critical edges are control flow edges where the source block has multiple outgoing edges and the
 * target has multiple incoming edges.
 *
 * <p>These worsen data flow analysis results and make Phi deconstruction complicated, so we design
 * our transformations in a way that these never happen.
 */
public class CriticalEdgeDetector implements Optimizer {
  @Override
  public boolean optimize(Graph graph) {
    seq(GraphUtils.topologicalOrder(graph))
        .ofType(Block.class)
        .forEach(CriticalEdgeDetector::checkForCriticalEdge);
    return false;
  }

  private static void checkForCriticalEdge(Block block) {
    NodeUtils.criticalEdges(block)
        .forEach(
            edge -> {
              throw new AssertionError(
                  "Unsplit critical edge detected in "
                      + block.getGraph()
                      + ": "
                      + edge.node
                      + " ("
                      + edge.node.getBlock()
                      + ") to "
                      + block
                      + "(pred #"
                      + edge.pos
                      + ")");
            });
  }
}
