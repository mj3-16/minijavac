package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

import com.google.common.collect.Iterables;
import firm.BackEdges;
import firm.Graph;
import firm.nodes.Block;
import firm.nodes.Jmp;
import firm.nodes.Node;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import org.jooq.lambda.tuple.Tuple3;

/** Removes {@link Block} nodes that contain a single {@link Jmp} node and nothing else. */
public class JmpBlockRemover extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    this.hasChanged = false;
    FirmUtils.withBackEdges(graph, () -> GraphUtils.reversePostOrder(graph).forEach(this::visit));
    return hasChanged;
  }

  @Override
  public void visit(Block block) {
    tryToIdentifyNewEdge(block)
        .ifPresent(
            edge -> {
              hasChanged = true;
              remove(block, edge.v1, edge.v2, edge.v3);
            });
  }

  private Optional<Tuple3<Node, Integer, Block>> tryToIdentifyNewEdge(Block block) {
    List<Node> nodesInBlock = nodesInBlock(block);
    boolean isJmpBlock = nodesInBlock.size() == 1 && nodesInBlock.get(0) instanceof Jmp;
    if (!isJmpBlock) {
      return Optional.empty();
    }
    Jmp jmp = (Jmp) nodesInBlock(block).get(0);

    if (block.getPredCount() != 1) {
      return Optional.empty();
    }

    BackEdges.Edge jmpToTargetEdge = Iterables.getOnlyElement(BackEdges.getOuts(jmp));
    Block jmpTarget = (Block) jmpToTargetEdge.node;
    Node jmpBlockPredecessor = Iterables.getOnlyElement(block.getPreds());

    boolean sourceHasMultipleExits = !NodeUtils.isSingleExitNode(jmpBlockPredecessor);
    if (sourceHasMultipleExits) {
      // Removal of the block would introduce a critical edge, so we don't do it.
      return Optional.empty();
    }

    return Optional.of(tuple(jmpBlockPredecessor, jmpToTargetEdge.pos, jmpTarget));
  }

  private List<Node> nodesInBlock(Block block) {
    return seq(BackEdges.getOuts(block)).map(e -> e.node).collect(Collectors.toList());
  }

  // This implementation is not capable of removing jmp-only blocks with more than one predecessor.
  // To implement this, the Phi nodes in the target block must also be adjusted.
  private void remove(Block block, Node source, int predIndex, Block target) {
    target.setPred(predIndex, source);
    Graph.killNode(block);
  }
}
