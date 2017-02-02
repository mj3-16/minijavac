package minijava.ir.optimize.licm;

import static minijava.ir.utils.NodeUtils.getPredecessorBlocks;

import firm.nodes.Block;
import firm.nodes.Node;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import minijava.ir.Dominance;

public class MoveInfo {

  public Block header;
  public Block lastUnduplicated;
  public int cost = 0;
  public LinkedHashSet<Node> toMove = new LinkedHashSet<>();

  public MoveInfo(Block header) {
    this.header = header;
    this.lastUnduplicated = header;
  }

  public Set<Block> blocksToDuplicate() {
    assert Dominance.dominates(header, lastUnduplicated);
    Set<Block> toVisit = getPredecessorBlocks(lastUnduplicated).toSet();
    Set<Block> toDuplicate = new HashSet<>();
    // DFS, the 432543534309th
    while (!toVisit.isEmpty()) {
      Block cur = toVisit.iterator().next();
      toVisit.remove(cur);
      if (toDuplicate.contains(cur) || !Dominance.dominates(header, cur)) {
        continue;
      }
      toDuplicate.add(cur);

      if (!cur.equals(header)) {
        // Otherwise we'll follow back edges, which is clearly not something we want.
        getPredecessorBlocks(cur).forEach(toVisit::add);
      }
    }
    return toDuplicate;
  }
}
