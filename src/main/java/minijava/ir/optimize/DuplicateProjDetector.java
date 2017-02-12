package minijava.ir.optimize;

import static minijava.ir.utils.GraphUtils.topologicalOrder;

import firm.BackEdges;
import firm.Graph;
import firm.nodes.Node;
import firm.nodes.Proj;
import java.util.ArrayList;
import minijava.ir.utils.FirmUtils;

public class DuplicateProjDetector extends BaseOptimizer {
  @Override
  public boolean optimize(Graph graph) {
    ArrayList<Node> nodes = topologicalOrder(graph);
    FirmUtils.withBackEdges(graph, () -> nodes.forEach(this::visit));
    return false;
  }

  @Override
  public void visit(Proj node) {
    // make sure there is no other Proj with the same num on the pred
    for (BackEdges.Edge be : BackEdges.getOuts(node.getPred())) {
      if (!(be.node instanceof Proj) || be.node.equals(node)) {
        continue;
      }
      int num = ((Proj) be.node).getNum();
      if (num == node.getNum()) {
        throw new Error(
            "Duplicate Proj with num "
                + num
                + " ("
                + node
                + ", "
                + be.node
                + ") "
                + " on "
                + node.getPred()
                + " in "
                + node.getGraph());
      }
    }
  }
}
