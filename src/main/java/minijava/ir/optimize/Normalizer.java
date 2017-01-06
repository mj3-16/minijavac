package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Places the constant argument of binary operations as the left argument if possible.
 *
 * <p>Constant folding has to be done before.
 */
public class Normalizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    hasChanged = false;
    BackEdges.enable(graph);
    List<Node> l = new ArrayList<>();
    graph.walkTopological(new ConsumingNodeVisitor(l::add));
    for (Node n : l) {
      n.accept(this);
    }
    BackEdges.disable(graph);
    return hasChanged;
  }

  @Override
  public void visit(Add node) {
    changeArgumentsIfNeeded(node);
  }

  @Override
  public void visit(Mul node) {
    changeArgumentsIfNeeded(node);
  }

  private void changeArgumentsIfNeeded(Binop binop) {
    Node left = binop.getLeft();
    Node right = binop.getRight();
    if (hasToChangeArguments(left, right)) {
      binop.setLeft(right);
      binop.setRight(left);
      hasChanged = true;
    }
  }

  public void visit(Sub node) {
    Node left = node.getLeft();
    Node right = node.getRight();
    if (hasToChangeArguments(left, right)) {
      Node negatedRight = graph.newMinus(left.getBlock(), right);
      Node newAdd = graph.newAdd(left.getBlock(), negatedRight, left);
      Graph.exchange(node, newAdd);
      hasChanged = true;
    }
  }

  private boolean hasToChangeArguments(Node left, Node right) {
    if (isConst(right)) {
      if (isConst(left)) {
        throw new RuntimeException("Run the normalizer after constant folding");
      }
      return true;
    }
    return false;
  }

  private boolean isConst(Node node) {
    return node.getOpCode() == binding_irnode.ir_opcode.iro_Const;
  }
}
