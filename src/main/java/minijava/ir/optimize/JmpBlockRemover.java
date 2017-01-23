package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

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
    tryToIdentifyRedirection(block)
        .ifPresent(
            redirection -> {
              hasChanged = true;
              remove(block, redirection);
            });
  }

  private Optional<Redirection> tryToIdentifyRedirection(Block block) {
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

    return Optional.of(new Redirection(jmpBlockPredecessor, jmpTarget, jmpToTargetEdge.pos));
  }

  private List<Node> nodesInBlock(Block block) {
    return seq(BackEdges.getOuts(block)).map(e -> e.node).collect(Collectors.toList());
  }

  // This implementation is not capable of removing jmp-only blocks with more than one predecessor.
  // To implement this, the Phi nodes in the target block must also be adjusted.
  private void remove(Block redirected, Redirection redirection) {
    redirection.target.setPred(redirection.predIndex, redirection.source);
    Graph.killNode(redirected);
  }

  private static class Redirection {
    public final Node source;
    public final Block target;
    public final int predIndex;

    private Redirection(Node source, Block target, int predIndex) {
      this.source = source;
      this.target = target;
      this.predIndex = predIndex;
    }
  }
}
