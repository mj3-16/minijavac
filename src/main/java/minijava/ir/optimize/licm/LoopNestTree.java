package minijava.ir.optimize.licm;

import static org.jooq.lambda.Seq.seq;

import firm.nodes.Block;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class LoopNestTree {
  public final Block header;
  public final Set<Block> loopBlocks;
  public LoopNestTree parent;
  public final List<LoopNestTree> children = new ArrayList<>();

  public LoopNestTree(Block header, Set<Block> loopBlocks) {
    this.header = header;
    this.loopBlocks = loopBlocks;
    this.parent = this;
  }

  public static LoopNestTree fromLoopBlocks(Map<Block, Set<Block>> loopBlocks) {
    Map<Block, Block> parents = new HashMap<>();
    for (Map.Entry<Block, Set<Block>> entry : loopBlocks.entrySet()) {
      Block header = entry.getKey();
      for (Map.Entry<Block, Set<Block>> potentialParent : loopBlocks.entrySet()) {
        Block candidate = potentialParent.getKey();
        Set<Block> enclosedBlocks = potentialParent.getValue();
        boolean isParent = enclosedBlocks.contains(header);
        if (!isParent) {
          continue;
        }

        Block parent = parents.get(header);
        boolean betterParent = parent == null || loopBlocks.get(parent).contains(candidate);

        if (betterParent) {
          parents.put(header, candidate);
        }
      }
    }

    Map<Block, LoopNestTree> nodes =
        seq(loopBlocks)
            .map(p -> new LoopNestTree(p.v1, p.v2))
            .toMap(node -> node.header, node -> node);

    Set<Block> possibleRoots = loopBlocks.keySet();
    for (Map.Entry<Block, Block> parentEdge : parents.entrySet()) {
      LoopNestTree child = nodes.get(parentEdge.getKey());
      LoopNestTree parent = nodes.get(parentEdge.getValue());
      parent.addChild(child);
      possibleRoots.remove(parentEdge.getKey());
    }

    assert possibleRoots.size() == 1;
    Block root = possibleRoots.iterator().next();
    return nodes.get(root);
  }

  private void addChild(LoopNestTree child) {
    LoopNestTree formerParent = child.parent;
    children.add(child);
    child.parent = this;
    boolean wasRoot = formerParent != child;
    if (wasRoot) {
      formerParent.children.remove(child);
    }
  }

  public Optional<LoopNestTree> findInnermostEnclosingLoop(Block block) {
    if (!loopBlocks.contains(block)) {
      return Optional.empty();
    }

    for (LoopNestTree child : children) {
      Optional<LoopNestTree> result = child.findInnermostEnclosingLoop(block);
      if (result.isPresent()) {
        return result;
      }
    }

    // No child contained the node.
    return Optional.of(this);
  }

  public void visitPostOrder(Consumer<LoopNestTree> visit) {
    for (LoopNestTree child : children) {
      child.visitPostOrder(visit);
    }
    visit.accept(this);
  }

  @Override
  public String toString() {
    return "LoopNestTree{" + header + loopBlocks + ", children=" + children + '}';
  }
}
