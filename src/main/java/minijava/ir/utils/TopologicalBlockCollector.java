package minijava.ir.utils;

import firm.BlockWalker;
import firm.Graph;
import firm.nodes.Block;
import java.util.ArrayList;
import java.util.List;

/** Collects the blocks of a graph in topological order. */
public class TopologicalBlockCollector implements BlockWalker {

  private List<Block> blocksInPostorder;

  public List<Block> getBlocksInTopologicalOrder(Graph graph) {
    blocksInPostorder = new ArrayList<>();
    graph.walkBlocksPostorder(this);
    System.err.println(blocksInPostorder);
    // Collections.reverse(blocksInPostorder);
    return blocksInPostorder;
  }

  @Override
  public void visitBlock(Block block) {
    blocksInPostorder.add(block);
  }
}
