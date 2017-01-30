package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.Phi;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import minijava.Cli;
import minijava.ir.Dominance;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import org.jooq.lambda.Seq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We practically do this through loop inversion: Copying the loop header and inserting the
 * invariant code there, while the back edge will still point to the old header, which won't repeat
 * the computation. That effectively transforms a while loop into a do/while loop.
 *
 * <p>This is all based on 'Compiler Design: Analysis and Transformation'.
 */
public class LoopInvariantCodeMotion extends BaseOptimizer {

  private static final Logger LOGGER = LoggerFactory.getLogger("LoopInvariantCodeMotion");
  private static final int DUPLICATE_NODES_THRESHOLD = 200;
  /**
   * Contains the edges of a forest of stars, where each node has an edge to the unique loop header.
   */
  private final Map<Block, Block> loopHeaders = new HashMap<>();
  /** Contains the nodes we move later on. This needs to preserve insertion order. */
  private final Set<Node> toMove = new LinkedHashSet<>();
  /** This is mostly used to cache determineBlocksToDuplicate. */
  private final Map<Block, Optional<MoveInfo>> determineBlocksToDuplicateCache = new HashMap<>();

  private final Map<Node, Node> duplicated = new HashMap<>();

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    loopHeaders.clear();
    toMove.clear();
    determineBlocksToDuplicateCache.clear();
    duplicated.clear();
    return FirmUtils.withBackEdges(
        graph,
        () -> {
          GraphUtils.topologicalOrder(graph).forEach(this::analyseLoopHeaders);
          return moveCode();
        });
  }

  private void analyseLoopHeaders(Node node) {
    if (NodeUtils.isTiedToBlock(node)) {
      return;
    }

    Block loopHeader = findLoopHeaderBlock((Block) node.getBlock());

    boolean canBeMovedOutside =
        seq(node.getPreds()).allMatch(pred -> definedOutsideLoopBody(loopHeader, pred));

    boolean isTooCostly = !determineBlocksToDuplicate((Block) node.getBlock()).isPresent();
    if (canBeMovedOutside && !isTooCostly) {
      toMove.add(node);
    }
  }

  private boolean definedOutsideLoopBody(Block loopHeader, Node pred) {
    // The pred was visible before, so either will be moved to this loop's trampoline
    // or an outer one. Either way, we can move this node, too.
    boolean willBeMovedOutside = toMove.contains(pred);
    return willBeMovedOutside || Dominance.strictlyDominates((Block) pred.getBlock(), loopHeader);
  }

  private Block findLoopHeaderBlock(Block block) {
    for (Block dominator : Dominance.dominatorPath(block)) {
      if (loopHeaders.containsKey(dominator)) {
        // We already did the hard work for the current dominator, the loop header of which
        // is also our loop header.
        // This should eventually hit, as the start block is always a candidate for dominator.
        return loopHeaders.get(dominator);
      }

      if (isLoopHeader(dominator)) {
        // Found the header block
        loopHeaders.put(dominator, dominator);
        return dominator;
      }
    }

    // For this case we still got a sane default
    loopHeaders.put(graph.getStartBlock(), graph.getStartBlock());
    return graph.getStartBlock();
  }

  private static boolean isLoopHeader(Block block) {
    return NodeUtils.hasIncomingBackEdge(block);
  }

  private Optional<MoveInfo> determineBlocksToDuplicate(Block block) {
    return determineBlocksToDuplicateCache.computeIfAbsent(
        block,
        b -> {
          // Which blocks have to be duplicated? Well, the loop header, for sure.
          // But there might also be other blocks in-between that need to be copied, namely everything
          // coming before the definition becomes visible. When does the definition become visible?
          // Well, it's only safe to move it as far up as post-dominance tells us. Thus the following
          // reasoning:
          //
          // We have to follow immediate dominators, and note the last one that ist post dominated by
          // current block. Going from there, we have to duplicate every predecessor block until
          // we hit the loop header. This also means that we have to duplicate loops, etc. This may
          // not be viable, that's why we count how many nodes we would have to copy.
          Block headerBlock = findLoopHeaderBlock(b);
          Block lastPostdom =
              Dominance.dominatorPath(b)
                  .limitUntil(dom -> !Dominance.postDominates(b, dom))
                  .reverse()
                  .findFirst()
                  .get(); // b itself is always a candidate

          // Now follow preds until we hit the header and record all for duplicates.
          // We eventually hit the header since it's a dominator.
          Set<Block> toVisit = getPredecessorBlocks(lastPostdom).toSet();
          Set<Block> toDup = new HashSet<>();
          int nodesToDuplicate = 0;
          // DFS, the 432543534309th
          while (!toVisit.isEmpty()) {
            Block cur = toVisit.iterator().next();
            toVisit.remove(cur);
            if (toDup.contains(cur) || !Dominance.dominates(headerBlock, cur)) {
              continue;
            }
            toDup.add(cur);

            nodesToDuplicate += BackEdges.getNOuts(cur);
            if (nodesToDuplicate > DUPLICATE_NODES_THRESHOLD) {
              // This is too much to copy to be worth it.
              return Optional.empty();
            }

            if (!cur.equals(headerBlock)) {
              // Otherwise we'll follow back edges, which is clearly not something we want.
              getPredecessorBlocks(cur).forEach(toVisit::add);
            }
          }
          return Optional.of(new MoveInfo(toDup, lastPostdom));
        });
  }

  private Seq<Block> getPredecessorBlocks(Block cur) {
    return seq(cur.getPreds()).map(Node::getBlock).cast(Block.class);
  }

  private static class MoveInfo {

    public final Set<Block> toDuplicate;
    public final Block lastDominated;

    private MoveInfo(Set<Block> toDuplicate, Block lastDominated) {
      this.toDuplicate = toDuplicate;
      this.lastDominated = lastDominated;
    }
  }

  private boolean moveCode() {
    System.out.println("toMove = " + toMove);
    for (Node move : toMove) {
      Block defBlock = (Block) move.getBlock();
      Block originalHeader = findLoopHeaderBlock(defBlock);
      // We need to save back edges for later. The duplicate header will be removed of all
      // back edges, while the original will have only back edges.
      Set<Node> backPreds =
          seq(originalHeader.getPreds())
              .filter(n -> Dominance.dominates(originalHeader, (Block) n.getBlock()))
              .toSet();

      MoveInfo info = determineBlocksToDuplicate(defBlock).get();
      System.out.println("info.toDuplicate = " + info.toDuplicate);
      System.out.println("info.lastDominated = " + info.lastDominated);
      for (Block original : info.toDuplicate) {
        if (duplicated.containsKey(original)) {
          continue;
        }

        Block duplicate = copyNode(original, info.toDuplicate);
        System.out.println("duplicate = " + duplicate);
        // We need to fix up successors, so that they also point to this duplicate.
        List<BackEdges.Edge> successors = NodeUtils.getControlFlowSuccessors(original).toList();
        for (BackEdges.Edge successor : successors) {
          if (info.toDuplicate.contains(successor.node) || duplicated.containsKey(successor.node)) {
            // This either already was handled or will be handled
            continue;
          }

          // If the successor is not to be duplicated, there must be at least one other successor.
          // That's because the single successor not to be duplicated is post-dominated by the
          // defBlock, so also the current block would be post dominated.
          // That's clearly not the case, since it is to be duplicated, so the following assertion
          // holds.
          assert successors.size() > 1;
          // Since there are no critical edges at this point, the current block has to be
          // the single predecessor.
          assert successor.node.getPredCount() == 1;
          System.out.println(successor.node);
          // We need to route our control flow from both blocks to the successor, meaning we would
          // introduce a critical edge. Thus, we need to insert intermediate blocks.
          Node originalJmp = successor.node.getPred(0);
          System.out.println("originalJmp = " + originalJmp);
          Node duplicateJmp = copyNode(originalJmp, info.toDuplicate);
          System.out.println("duplicateJmp = " + duplicateJmp);
          assert duplicateJmp.getBlock().equals(duplicate);
          Block fromOriginal = (Block) graph.newBlock(new Node[] {originalJmp});
          Block fromDuplicate = (Block) graph.newBlock(new Node[] {duplicateJmp});
          // This next block will render successor.node redundant,
          // but we let the JmpBlockRemover figure this out.
          Block landingBlock =
              (Block)
                  graph.newBlock(
                      new Node[] {graph.newJmp(fromOriginal), graph.newJmp(fromDuplicate)});
          // TODO: Phis here
          successor.node.setPred(0, graph.newJmp(landingBlock));
        }
      }

      boolean duplicatedLoopHeader = duplicated.containsKey(originalHeader);
      if (duplicatedLoopHeader) {
        Block duplicateHeader = (Block) duplicated.get(originalHeader);
        System.out.println("backPreds = " + backPreds);
        System.out.println("originalHeader = " + originalHeader);
        System.out.println("duplicateHeader = " + duplicateHeader);

        // We fix up the predecessors. The original header will only keep back edges,
        // the duplicate header will only keep the forward edges on loop entry.
        distributePredecessors(originalHeader, duplicateHeader, backPreds);

        // We changed dominance and with it the loop header, which invalidated our loopHeader
        // information.
        loopHeaders.clear();

        // Now that dominance changed, we have to identify the new loop header and reorganize Phis.
        Block newHeader = findLoopHeaderBlock(defBlock);

        // The original loop header won't be loop header after this, so we'll delete the keep edge.
        Node end = graph.getEnd();
        int n = end.getPredCount();
        for (int i = 0; i < n; ++i) {
          Node kept = end.getPred(i);
          if (originalHeader.equals(kept)) {
            end.setPred(i, newHeader);
          } else if (originalHeader.equals(kept.getBlock())) {
            // TODO: Phi M
            end.setPred(i, graph.newBad(Mode.getM()));
          }
        }
      }

      // After this transformation, we really should find the right place to put the invariant code.
      Block postdominatorBeforeLoop = findPostdominatorBeforeLoop(defBlock).get();
      System.out.println("postdominatorBeforeLoop = " + postdominatorBeforeLoop);
      move.setBlock(postdominatorBeforeLoop);

      Cli.dumpGraphIfNeeded(graph, "gldskg");
    }

    return true;
  }

  /**
   * This distributes {@param backPreds} such that {@param originalBlock} will keep all forward
   * edges and {@param duplicateHeader} will keep all back edges.
   */
  private void distributePredecessors(
      Block originalHeader, Block duplicateHeader, Set<Node> backPreds) {
    List<Phi> originalPhis =
        seq(NodeUtils.getNodesInBlock(originalHeader)).ofType(Phi.class).toList();
    List<Phi> duplicatePhis =
        seq(NodeUtils.getNodesInBlock(duplicateHeader)).ofType(Phi.class).toList();

    int n = originalHeader.getPredCount();
    assert originalHeader.getPredCount() == duplicateHeader.getPredCount();
    for (int i = 0; i < n; ++i) {
      Node pred = originalHeader.getPred(i);
      assert pred.equals(duplicateHeader.getPred(i));
      if (backPreds.contains(pred)) {
        // Back edges may only point to the origin header (which actually makes them not really
        // back edges any more).
        duplicateHeader.setPred(i, graph.newBad(Mode.getX()));
        final int j = i;
        duplicatePhis.forEach(phi -> phi.setPred(j, graph.newBad(phi.getMode())));
      } else {
        // This is a regular edge with which the loop is entered. The loop is only menat to be
        // entered from the duplicateHeader now.
        originalHeader.setPred(i, graph.newBad(Mode.getX()));
        final int j = i;
        originalPhis.forEach(phi -> phi.setPred(j, graph.newBad(phi.getMode())));
      }
    }
  }

  private Optional<Block> findPostdominatorBeforeLoop(Block defBlock) {
    // The goal of the transformation is to get a block outside the loop (e.g. not dominated by
    // the loop header) that is post-dominated by the block with the invariant code we want to move.
    // This is so that we can be sure that we don't compute the definition when we don't enter the
    // loop.
    // For finding this block, we take the immedate dom of the loop header of the defining
    // block, knowing that this block will be post-dominated by the def.
    Block header = findLoopHeaderBlock(defBlock);
    return Dominance.immediateDominator(header)
        .map(
            idom -> {
              // This is the whole point of the transformation.
              // The idom must be a jmp block (e.g. one with only one successor), since
              // the loop header would introduce a critical edge otherwise.
              // That's where we put our code.
              assert Dominance.postDominates(defBlock, idom);
              return idom;
            });
  }

  /**
   * We only really copy {@param node} if it's in a block that's going to be duplicated. Also we
   * never duplicate the same node twice.
   */
  private <T extends Node> T copyNode(T node, Set<Block> blocksToDuplicate) {
    if (duplicated.containsKey(node)) {
      return (T) duplicated.get(node);
    }

    Node originalBlock = node.getBlock();
    if (originalBlock == null) {
      originalBlock = node;
    }
    if (!blocksToDuplicate.contains(originalBlock)) {
      // We don't duplicate this node and return the original.
      return node;
    }

    T copy = (T) graph.copyNode(node);
    NodeUtils.setLink(copy, NodeUtils.getLink(node));
    duplicated.put(node, copy);
    duplicated.put(copy, copy); // Just in case some reference was already updated
    if (node.getBlock() != null) {
      copy.setBlock(copyNode(copy.getBlock(), blocksToDuplicate));
    }
    final int n = copy.getPredCount();
    for (int i = 0; i < n; ++i) {
      Node pred = copy.getPred(i);
      copy.setPred(i, copyNode(pred, blocksToDuplicate));
    }
    return copy;
  }
}
