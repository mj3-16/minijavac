package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import java.util.stream.StreamSupport;
import minijava.ir.DefaultNodeVisitor;

/**
 * Replaces {@link Cond} nodes (or more precisely, their accompanying {@link Proj} nodes) with
 * {@link Jmp} nodes, if the condition is constant.
 *
 * <p>The {@link Proj} node that is no longer relevant is replaced with a {@link Bad} node. A
 * subsequent run of an {@link Optimizer} that removes such nodes is required.
 */
public class ConstantControlFlowOptimizer implements DefaultNodeVisitor, Optimizer {

  private Graph graph;
  private Proj trueProj;
  private Proj falseProj;
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
      determineProjectionNodes(node);
      hasChanged = true;
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
    Proj[] projs =
        StreamSupport.stream(BackEdges.getOuts(node).spliterator(), false)
            .map(e -> (Proj) e.node)
            .toArray(size -> new Proj[size]);
    assert projs.length == 2;
    if (projs[0].getNum() == Cond.pnTrue) {
      trueProj = projs[0];
      falseProj = projs[1];
    } else {
      trueProj = projs[1];
      falseProj = projs[0];
    }
  }
}
