package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import minijava.ir.utils.NodeUtils;

/**
 * Replaces {@link Cond} nodes (or more precisely, their accompanying {@link Proj} nodes) with
 * {@link Jmp} nodes, if the condition is constant.
 *
 * <p>The {@link Proj} node that is no longer relevant is replaced with a {@link Bad} node. A
 * subsequent run of an {@link Optimizer} that removes such nodes is required.
 */
public class ConstantControlFlowOptimizer extends NodeVisitor.Default implements Optimizer {

  private Graph graph;
  private boolean hasChanged;

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    hasChanged = false;
    BackEdges.enable(graph);
    graph.walkTopological(this);
    BackEdges.disable(graph);
    return hasChanged;
  }

  @Override
  public void visit(Cond node) {
    if (node.getSelector() instanceof Const) {
      TargetValue condition = ((Const) node.getSelector()).getTarval();
      NodeUtils.CondProjs projs = NodeUtils.determineProjectionNodes(node);
      hasChanged = true;
      if (condition.equals(TargetValue.getBTrue())) {
        Graph.exchange(projs.true_, graph.newJmp(node.getBlock()));
        Graph.exchange(projs.false_, graph.newBad(Mode.getANY()));
      } else {
        Graph.exchange(projs.false_, graph.newJmp(node.getBlock()));
        Graph.exchange(projs.true_, graph.newBad(Mode.getANY()));
      }
    }
  }
}
