package minijava.ir.assembler;

import firm.nodes.Block;
import firm.nodes.Node;
import firm.nodes.Phi;
import java.util.*;

public class LostCopyPronePhiDetector {

  private final Map<Phi, Boolean> isPhiCritical;

  public LostCopyPronePhiDetector() {
    isPhiCritical = new HashMap<>();
  }

  public boolean isPhiProneToLostCopies(Phi phi) {
    if (!isPhiCritical.containsKey(phi)) {
      isPhiCritical.put(phi, checkPhi(phi));
    }
    return isPhiCritical.get(phi);
  }

  private boolean checkPhi(Phi phi) {
    Block phiBlock = (Block) phi.getBlock();
    Set<Integer> visitedNodeIds = new HashSet<>();
    Stack<Node> toVisit = new Stack<Node>();
    toVisit.add(phi);
    while (!toVisit.isEmpty()) {
      Node currentNode = toVisit.pop();
      visitedNodeIds.add(currentNode.getNr());
      for (Node node : currentNode.getPreds()) {
        if (node.equals(phi)) {
          return true;
        }
        if (!visitedNodeIds.contains(node.getNr())) {
          toVisit.push(node);
        }
      }
    }
    return false;
  }
}
