package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Replaces {@link Cond} nodes (or more precisely, their accompanying {@link Proj} nodes) with
 * {@link Jmp} nodes, if the condition is constant.
 *
 * <p>The {@link Proj} node that is no longer relevant is replaced with a {@link Bad} node. A
 * subsequent run of an {@link Optimizer} that removes such nodes is required.
 */
public class ConstantControlFlowOptimizer extends DefaultNodeVisitor implements Optimizer {

  private Graph graph;
  private Proj trueProj;
  private Proj falseProj;

  @Override
  public void optimize(Graph graph) {
    this.graph = graph;
    BackEdges.enable(graph);
    graph.walkTopological(this);
    BackEdges.disable(graph);
  }

  @Override
  public void visit(Cond node) {
    if (node.getSelector() instanceof Const) {
      TargetValue condition = ((Const) node.getSelector()).getTarval();
      determineProjectionNodes(node);
      if (condition.equals(TargetValue.getBTrue())) {
        graph.exchange(trueProj, graph.newJmp(node.getBlock()));
        graph.exchange(falseProj, graph.newBad(Mode.getANY()));
      } else {
        graph.exchange(falseProj, graph.newJmp(node.getBlock()));
        graph.exchange(trueProj, graph.newBad(Mode.getANY()));
      }
    }
  }

  private void determineProjectionNodes(Cond node) {
    List<Proj> projNodes =
        StreamSupport.stream(BackEdges.getOuts(node).spliterator(), false)
            .map(e -> (Proj) e.node)
            .collect(Collectors.toList());
    assert projNodes.size() == 2;
    // TODO: the order of the condition's projection nodes is not documented...
    trueProj = projNodes.get(0);
    falseProj = projNodes.get(1);
  }
}
