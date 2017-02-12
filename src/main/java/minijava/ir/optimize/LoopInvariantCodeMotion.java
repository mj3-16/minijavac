package minijava.ir.optimize;

import static firm.bindings.binding_irnode.ir_opcode.iro_Bad;
import static minijava.ir.utils.NodeUtils.incomingBackEdges;
import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Sets;
import firm.BackEdges;
import firm.BackEdges.Edge;
import firm.Graph;
import firm.Mode;
import firm.nodes.Block;
import firm.nodes.End;
import firm.nodes.Node;
import firm.nodes.Phi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import minijava.ir.Dominance;
import minijava.ir.optimize.licm.LoopNestTree;
import minijava.ir.optimize.licm.MoveInfo;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;
import minijava.ir.utils.NodeUtils;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;

/**
 * We practically do this through loop inversion: Copying the loop header and inserting the
 * invariant code there, while the back edge will still point to the old header, which won't repeat
 * the computation. That effectively transforms a while loop into a do/while loop.
 *
 * <p>This is all based on 'Compiler Design: Analysis and Transformation' and Apple's 'Modern
 * Compiler Implementation in ML'.
 */
public class LoopInvariantCodeMotion extends BaseOptimizer {

  /** Contains information on which nodes to move per loop header. */
  private final Map<Block, MoveInfo> moveInfos = new HashMap<>();
  /** Notes already duplicated nodes while transforming a loop. */
  private final Map<Node, Node> duplicated = new HashMap<>();
  /**
   * Remembers definitions of a node that are visible in a given block during SSA reconstruction.
   */
  private final Map<Node, Map<Block, Node>> visibleDefinitions = new HashMap<>();
  /** LoopNestTree of the given graph as constructed in the initial analysis. */
  private LoopNestTree loopNestTree;

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    moveInfos.clear();
    duplicated.clear();
    visibleDefinitions.clear();
    return FirmUtils.withBackEdges(
        graph,
        () -> {
          ArrayList<Node> order = GraphUtils.topologicalOrder(graph);
          loopNestTree = buildLoopNestTree(order);
          order.forEach(this::evaluateMovability);
          return moveCode();
        });
  }

  private LoopNestTree buildLoopNestTree(ArrayList<Node> allNodes) {
    Map<Block, Set<Block>> loopBlocks = new HashMap<>();
    Set<Block> allBlocks = new HashSet<>();
    for (Block header : seq(allNodes).ofType(Block.class)) {
      allBlocks.add(header);
      if (!isLoopHeader(header)) {
        continue;
      }

      Set<Block> body = blocksOfLoop(header);
      loopBlocks.put(header, body);
    }

    // We also add a pseudo node for nodes not in a loop, which will be the root.
    loopBlocks.put(graph.getStartBlock(), allBlocks);

    return LoopNestTree.fromLoopBlocks(loopBlocks);
  }

  private void evaluateMovability(Node node) {
    if (NodeUtils.isTiedToBlock(node)) {
      return;
    }

    Block defBlock = (Block) node.getBlock();
    // We should at the very least find the root, represented by the start block.
    LoopNestTree enclosingLoop = loopNestTree.findInnermostEnclosingLoop(defBlock).get();
    boolean isPartOfALoop = !enclosingLoop.header.equals(graph.getStartBlock());
    if (!isPartOfALoop) {
      return;
    }

    Block loopHeader = enclosingLoop.header;
    MoveInfo info = getMoveInfoOf(loopHeader);
    Set<Block> loopBody = enclosingLoop.loopBlocks;
    boolean canBeMovedOutside =
        seq(node.getPreds())
            .filter(n -> !info.toMove.contains(n))
            .map(Node::getBlock)
            .noneMatch(loopBody::contains);
    if (!canBeMovedOutside) {
      return;
    }

    Seq<Block> loopFooters =
        incomingBackEdges(loopHeader)
            .map(loopHeader::getPred)
            .map(Node::getBlock)
            .cast(Block.class);
    boolean isVisibleAfterFirstIteration =
        loopFooters.allMatch(f -> Dominance.dominates(defBlock, f));
    if (!isVisibleAfterFirstIteration) {
      return;
    }

    extendUnrolling(loopHeader, node);
  }

  private void extendUnrolling(Block loopHeader, Node node) {
    MoveInfo info = getMoveInfoOf(loopHeader);
    Block definingBlock = (Block) node.getBlock();
    // Which blocks have to be duplicated? Well, the loop header, for sure.
    // But there might also be other blocks in-between that need to be copied, namely everything
    // coming before the definition becomes visible. When does the definition become visible?
    // It's only safe to move it as far up as post-dominance tells us. Thus the following
    // reasoning:
    //
    // We have to follow immediate dominators, and note the last one that is post dominated by
    // the defining block. Going from there, we have to duplicate every predecessor block until
    // we hit the loop header. This also means that we have to duplicate loops, etc. This may
    // not be viable, that's why we count how many nodes we would have to copy.
    Block lastUnduplicated =
        Dominance.dominatorPath(definingBlock)
            .limitUntil(dom -> !Dominance.postDominates(definingBlock, dom))
            .reverse()
            .findFirst()
            .orElse(info.lastUnduplicated); // definingBlock itself is always a candidate

    if (Dominance.strictlyDominates(lastUnduplicated, info.lastUnduplicated)) {
      // lastUnduplicated would be copied anyway, so we don't need to move it.
      return;
    }

    info.toMove.add(node);
    if (info.lastUnduplicated.equals(lastUnduplicated)) {
      return;
    }
    info.lastUnduplicated = lastUnduplicated;

    // Now follow preds until we hit the header and record all for duplicates.
    // We eventually hit the the previous lastUnduplicated block, which is a dominator.
    assert Dominance.dominates(info.lastUnduplicated, lastUnduplicated);
    info.lastUnduplicated = lastUnduplicated;
    Set<Block> toDuplicate = info.blocksToDuplicate();

    // Nodes which were to be moved and are now in a block to be duplicated no longer have to be
    // moved, as they are copied and CSE will take care of the rest.
    List<Node> nodesToBeDuplicated =
        seq(info.toMove).filter(n -> toDuplicate.contains((Block) n.getBlock())).toList();
    info.toMove.removeAll(nodesToBeDuplicated);
  }

  private MoveInfo getMoveInfoOf(Block loopHeader) {
    return moveInfos.computeIfAbsent(loopHeader, MoveInfo::new);
  }

  private static Set<Block> blocksOfLoop(Block header) {
    Set<Block> reachable = new HashSet<>();
    Set<Block> toVisit = Sets.newHashSet(header);
    while (!toVisit.isEmpty()) {
      Block cur = toVisit.iterator().next();
      toVisit.remove(cur);
      boolean notPartOfLoop = !Dominance.dominates(header, cur);
      if (reachable.contains(cur) || notPartOfLoop) {
        continue;
      }
      reachable.add(cur);
      Seq<Block> cfgPreds =
          seq(cur.getPreds())
              .filter(n -> n.getMode().equals(Mode.getX()))
              .map(Node::getBlock)
              .cast(Block.class);
      cfgPreds.forEach(toVisit::add);
    }
    return reachable;
  }

  private Block enclosingLoopHeader(Block block) {
    for (Block dominator : Dominance.dominatorPath(block)) {
      if (isLoopHeader(dominator)) {
        return dominator;
      }
    }

    assert false : "Couldn't find loop header of " + block;

    // For this case we still got a sane default
    return graph.getStartBlock();
  }

  private static boolean isLoopHeader(Block block) {
    return NodeUtils.hasIncomingBackEdge(block);
  }

  private boolean moveCode() {
    loopNestTree.visitPostOrder(
        loop -> {
          // Everything duplicated up until now is considered an original.
          duplicated.clear();
          visibleDefinitions.clear();
          Block originalHeader = loop.header;
          MoveInfo info = getMoveInfoOf(originalHeader);
          if (info.toMove.isEmpty()) {
            return;
          }
          hasChanged = true;

          Set<Block> toDuplicate = info.blocksToDuplicate();

          // We need to save back edges for later. The duplicate header will be removed of all
          // back edges, while the original will have only back edges.
          Set<Node> backPreds =
              seq(originalHeader.getPreds())
                  .filter(n -> Dominance.dominates(originalHeader, (Block) n.getBlock()))
                  .toSet();

          // usages that aren't duplicated and point to the original defs that might
          // not be visible and need merging through Phis.
          List<BackEdges.Edge> dirtyUsages = dirtyUsages(toDuplicate);

          // We partly unroll the loop until we can move the invariant code out of the loop
          // We may only do that if there is a block before the loop that is post-dominated by the
          // original definition, so this is necessary.
          unrollAndFixControlFlow(originalHeader, toDuplicate, backPreds);

          // Control flow is fixed now. What remains is to copy/move instructions from the original
          // and also fixing up all dirtyUsages.

          // We put the invariant code in a post dominator of the loop body before the loop.
          Block newHeader = enclosingLoopHeader(info.lastUnduplicated);
          Block postdominatorBeforeLoop = postdominatorBeforeLoop(newHeader).get();
          for (Node move : info.toMove) {
            move.setBlock(postdominatorBeforeLoop);
          }

          // Every invariant node is in its new block. We can fix other unduplicated usages now
          // by inserting the necessary Phis.
          reconstructSSA(toDuplicate, dirtyUsages);

          // Identify the new loop header and reorganize keeps.
          // The original loop header won't be loop header after this, so we'll delete the keep edge.
          fixKeepEdges(originalHeader, newHeader);
        });
    return hasChanged;
  }

  private List<Edge> dirtyUsages(Set<Block> toDuplicate) {
    // usages that point to the original defs that might not be visible and need merging through
    // Phis. This is due to the back edge and consequently the loop header block changing.
    return seq(toDuplicate)
        .map(NodeUtils::getNodesInBlock)
        .flatMap(Seq::seq)
        .map(BackEdges::getOuts)
        .flatMap(Seq::seq)
        .filter(be -> isDirtyUsage(toDuplicate, be))
        .toList();
  }

  private static boolean isDirtyUsage(Set<Block> toDuplicate, Edge be) {
    return !toDuplicate.contains(getUsageBlock(be));
  }

  /**
   * For Phis, the actual use block is the pred block where the used definition is visible. Returns
   * null in the case that for when it's a Phi and its block has a Bad predecessor at the usages
   * index. (Otherwise it would return the block of the Bad, which is the start block).
   */
  @Nullable
  private static Block getUsageBlock(Edge usage) {
    Block usageBlock = (Block) usage.node.getBlock();
    if (usage.node instanceof Phi) {
      Node pred = usageBlock.getPred(usage.pos);
      if (pred.getOpCode() == iro_Bad) {
        return null;
      }
      usageBlock = (Block) pred.getBlock();
    }
    return usageBlock;
  }

  private void fixKeepEdges(Block originalHeader, Block newHeader) {
    Phi phiLoop =
        seq(NodeUtils.getNodesInBlock(newHeader))
            .ofType(Phi.class)
            .filter(phi -> phi.getMode().equals(Mode.getM()))
            .findFirst()
            .get();

    Node end = graph.getEnd();
    int n = end.getPredCount();
    for (int i = 0; i < n; ++i) {
      Node kept = end.getPred(i);
      if (originalHeader.equals(kept)) {
        end.setPred(i, newHeader);
      } else if (originalHeader.equals(kept.getBlock())) {
        end.setPred(i, phiLoop);
        phiLoop.setLoop(1);
      }
    }
  }

  private void unrollAndFixControlFlow(
      Block originalHeader, Set<Block> toDuplicate, Set<Node> backPreds) {
    // First duplicate blocks and fix up resulting control flow joints. Phis are handled later.
    for (Block original : toDuplicate) {
      Block duplicate = copyNode(original, toDuplicate);
      joinDuplicateControlFlow(toDuplicate, original, duplicate);
    }

    boolean duplicatedLoopHeader = duplicated.containsKey(originalHeader);
    if (duplicatedLoopHeader) {
      Block duplicateHeader = (Block) duplicated.get(originalHeader);

      // We fix up the predecessors. The original header will only keep back edges,
      // the duplicate header will only keep the forward edges on loop entry.
      distributePredecessors(originalHeader, duplicateHeader, backPreds);
    }
  }

  private void joinDuplicateControlFlow(Set<Block> toDuplicate, Block original, Block duplicate) {
    // We need to fix up successors, so that they also point to this duplicate.
    List<Edge> successors = NodeUtils.getControlFlowSuccessors(original).toList();
    for (Edge successor : successors) {
      if (toDuplicate.contains(successor.node)) {
        // There is nothing to join here. original and duplicate have separate successor
        // blocks.
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
      Node originalJmp = successor.node.getPred(0);
      Node duplicateJmp = copyNode(originalJmp, toDuplicate);
      assert duplicateJmp.getBlock().equals(duplicate);
      // This next block will render successor.node redundant,
      // but we let the JmpBlockRemover figure this out.
      Block landingBlock = (Block) graph.newBlock(new Node[] {originalJmp, duplicateJmp});
      successor.node.setPred(0, graph.newJmp(landingBlock));
      // Now the preceding blocks have two successors and the landing block has two predecessors.
      // We have to split the critical edges.
      NodeUtils.splitCriticalEdge(landingBlock, 0);
      NodeUtils.splitCriticalEdge(landingBlock, 1);
    }
    Dominance.invalidateDominace();
  }

  /** Reconstructs SSA form for a set of usages pointing to the original of a duplicate node. */
  private void reconstructSSA(Set<Block> toDuplicate, List<BackEdges.Edge> unduplicatedUsages) {
    for (BackEdges.Edge usage : unduplicatedUsages) {
      if (usage.node instanceof End) {
        // We ignore keep edges here
        continue;
      }
      if (usage.node instanceof Block) {
        // Blocks have already been handled
        continue;
      }

      Node originalDef = usage.node.getPred(usage.pos);
      Block originalBlock = (Block) originalDef.getBlock();
      Node duplicateDef = copyNode(originalDef, toDuplicate);
      Block usageBlock = getUsageBlock(usage);
      if (usageBlock == null) {
        // This can happen when the usage was a Phi, which still has a pred, but the containing
        // block has a Bad at that position (the case when it's the loop header), indicating
        // that the incoming control flow edge will be deleted. We don't fix the usage in that case.
        continue;
      }

      Mode mode = usage.node.getPred(usage.pos).getMode();
      Map<Block, Node> defsInBlock =
          visibleDefinitions.computeIfAbsent(originalDef, d -> new HashMap<>());

      defsInBlock.put(originalBlock, originalDef);

      Node mergedDef =
          searchDefinitionInsertingPhis(usageBlock, mode, duplicateDef, defsInBlock, false);
      usage.node.setPred(usage.pos, mergedDef);
    }
    Dominance.invalidateDominace();
  }

  private Node searchDefinitionInsertingPhis(
      Block usage, Mode mode, Node fallbackDef, Map<Block, Node> visibleDefs, boolean mayFallBack) {
    if (visibleDefs.containsKey(usage)) {
      return visibleDefs.get(usage);
    }

    if (usage.equals(fallbackDef.getBlock()) && mayFallBack) {
      return fallbackDef;
    }

    assert !graph.getStartBlock().equals(usage);

    List<Node> cfgPreds = seq(usage.getPreds()).filter(n -> n.getOpCode() != iro_Bad).toList();

    if (cfgPreds.size() == 1) {
      Block predBlock = (Block) cfgPreds.get(0).getBlock();
      return searchDefinitionInsertingPhis(predBlock, mode, fallbackDef, visibleDefs, true);
    }

    // We surely need to merge definitions.
    Node dummy = graph.newDummy(mode);
    Node[] dummies = Seq.generate(dummy).limit(cfgPreds.size()).toArray(Node[]::new);
    Node newDef = graph.newPhi(usage, dummies, mode);
    visibleDefs.put(usage, newDef);
    Set<Node> reachedDefs = new HashSet<>();
    for (int i = 0; i < cfgPreds.size(); ++i) {
      Block predBlock = (Block) cfgPreds.get(i).getBlock();
      Node def;
      if (predBlock == null) {
        def = graph.newBad(mode);
      } else {
        def = searchDefinitionInsertingPhis(predBlock, mode, fallbackDef, visibleDefs, true);
      }
      newDef.setPred(i, def);
      if (!newDef.equals(def)) {
        // We record unique new defs, other than the Phi itself.
        reachedDefs.add(def);
      }
    }

    if (reachedDefs.size() == 1 && !mode.equals(Mode.getM())) {
      // ... So that we can simplify looping Phis if their value is invariant.
      newDef = reachedDefs.iterator().next();
      visibleDefs.put(usage, newDef);
    }

    return newDef;
  }

  /**
   * This distributes {@param backPreds} such that {@param originalBlock} will keep all forward
   * edges and {@param duplicateHeader} will keep all back edges.
   */
  private void distributePredecessors(
      Block originalHeader, Block duplicateHeader, Set<Node> backPreds) {
    int n = originalHeader.getPredCount();
    assert originalHeader.getPredCount() == duplicateHeader.getPredCount();
    for (int i = 0; i < n; ++i) {
      Node pred = originalHeader.getPred(i);
      assert pred.equals(duplicateHeader.getPred(i));
      if (backPreds.contains(pred)) {
        // Back edges may only point to the origin header (which actually makes them not really
        // back edges any more).
        duplicateHeader.setPred(i, graph.newBad(Mode.getX()));
      } else {
        // This is a regular edge with which the loop is entered. The loop is only menat to be
        // entered from the duplicateHeader now.
        originalHeader.setPred(i, graph.newBad(Mode.getX()));
      }
    }
    Dominance.invalidateDominace();
  }

  private Optional<Block> postdominatorBeforeLoop(Block header) {
    // The goal of the transformation is to get a block outside the loop (e.g. not dominated by
    // the loop header) that is post-dominated by the block with the invariant code we want to move.
    // This is so that we can be sure that we don't compute the definition when we don't enter the
    // loop.
    // For finding this block, we take the immedate dom of the loop header of the defining
    // block, knowing that this block will be post-dominated by the def.
    return Dominance.immediateDominator(header)
        .map(
            idom -> {
              // This is the whole point of the transformation.
              // The idom must be a jmp block (e.g. one with only one successor), since
              // the loop header would introduce a critical edge otherwise.
              // That's where we put our code.
              assert Dominance.postDominates(header, idom);
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

    if (copy instanceof Block) {
      Dominance.invalidateDominace();
    }

    return copy;
  }
}
