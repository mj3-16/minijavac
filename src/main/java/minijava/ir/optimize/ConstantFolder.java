package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.TargetValue;
import firm.nodes.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;

public class ConstantFolder extends DefaultNodeVisitor implements Optimizer {

  private Map<Node, TargetValue> latticeMap;
  private Graph graph;
  private boolean hasChanged;

  @Override
  public void optimize(Graph graph) {
    this.latticeMap = new HashMap<>();
    this.graph = graph;
    BackEdges.enable(graph);
    fixedPointIteration();
    replaceConstants();
    BackEdges.disable(graph);
  }

  private void fixedPointIteration() {
    Worklist worklist = Worklist.fillTopological(graph);
    while (!worklist.isEmpty()) {
      Node n = worklist.pop();
      hasChanged = false;
      n.accept(this);
      if (hasChanged) {
        for (BackEdges.Edge e : BackEdges.getOuts(n)) {
          worklist.addFirst(e.node);
        }
      }
    }
  }

  private void replaceConstants() {
    for (Entry<Node, TargetValue> e : latticeMap.entrySet()) {
      if (e.getValue().isConstant()) {
        Node constant = graph.newConst(e.getValue());
        Graph.exchange(e.getKey(), constant);
      }
    }
  }

  @Override
  public void visit(Add node) {
    visitBinaryOperation((lhs, rhs) -> lhs.add(rhs), node, node.getLeft(), node.getRight());
  }

  /**
   * @param operation Describes how to calculate the constant value of {@code node}, if both {@code
   *     left} and {@code right} are constant. The inputs to this function are the constant values
   *     of {@code left} and {@code right}.
   * @param node {@link Add}, {@link And}, {@link Div}, ...
   * @param left the left child of {@code node}
   * @param right the right child of {@code node}
   */
  private void visitBinaryOperation(
      BiFunction<TargetValue, TargetValue, TargetValue> operation,
      Node node,
      Node left,
      Node right) {
    TargetValue newValue;
    if (isUnknown(left) || isUnknown(right)) {
      newValue = TargetValue.getUnknown();
    } else if (isConstant(left) && isConstant(right)) {
      TargetValue lhs = latticeMap.get(left);
      TargetValue rhs = latticeMap.get(right);
      newValue = operation.apply(lhs, rhs);
    } else {
      newValue = TargetValue.getBad();
    }
    TargetValue previousValue = latticeMap.put(node, newValue);
    hasChanged = previousValue != newValue;
  }

  boolean isUnknown(Node n) {
    TargetValue value = latticeMap.get(n);
    return value == null || value == TargetValue.getUnknown();
  }

  boolean isConstant(Node n) {
    TargetValue value = latticeMap.get(n);
    return value != null && value.isConstant();
  }

  @Override
  public void visit(And node) {
    visitBinaryOperation((lhs, rhs) -> lhs.and(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Cmp node) {
    visitBinaryOperation(
        (lhs, rhs) -> {
          // TODO: not sure how contains works, write tests for this!
          if (lhs.compare(rhs).contains(node.getRelation())) {
            return TargetValue.getBTrue();
          } else {
            return TargetValue.getBFalse();
          }
        },
        node,
        node.getLeft(),
        node.getRight());
  }

  @Override
  public void visit(Const node) {
    TargetValue newValue = node.getTarval();
    TargetValue previousValue = latticeMap.put(node, newValue);
    hasChanged = previousValue != newValue;
  }

  @Override
  public void visit(Div node) {
    visitBinaryOperation((lhs, rhs) -> lhs.div(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Minus node) {
    Node child = node.getOp();
    TargetValue newValue;
    if (isUnknown(child)) {
      newValue = TargetValue.getUnknown();
    } else if (isConstant(child)) {
      TargetValue value = latticeMap.get(child);
      newValue = value.neg();
    } else {
      newValue = TargetValue.getBad();
    }
    TargetValue previousValue = latticeMap.put(node, newValue);
    hasChanged = previousValue != newValue;
  }

  @Override
  public void visit(Mod node) {
    visitBinaryOperation((lhs, rhs) -> lhs.mod(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Mul node) {
    visitBinaryOperation((lhs, rhs) -> lhs.mul(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Sub node) {
    visitBinaryOperation((lhs, rhs) -> lhs.sub(rhs), node, node.getLeft(), node.getRight());
  }
}
