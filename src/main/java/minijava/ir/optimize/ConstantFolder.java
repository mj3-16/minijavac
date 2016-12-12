package minijava.ir.optimize;

import com.google.common.collect.Iterables;
import firm.BackEdges;
import firm.Graph;
import firm.TargetValue;
import firm.nodes.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;

public class ConstantFolder extends BaseOptimizer {

  private Map<Node, TargetValue> latticeMap;

  private TargetValue previousValue;
  private TargetValue newValue;
  private NodeVisitor phiStrategy;

  @Override
  public boolean optimize(Graph graph) {
    this.latticeMap = new HashMap<>();
    this.graph = graph;
    BackEdges.enable(graph);
    phiStrategy = new SupremumStrategy();
    fixedPointIteration();
    phiStrategy = new EqualAndConstantStrategy();
    fixedPointIteration();
    boolean changed = replaceConstants();
    BackEdges.disable(graph);
    return changed;
  }

  private boolean replaceConstants() {
    boolean nodesChanged = false;
    for (Entry<Node, TargetValue> e : latticeMap.entrySet()) {
      if (e.getValue().isConstant()) {
        Node constant = graph.newConst(e.getValue());
        Graph.exchange(e.getKey(), constant);
        nodesChanged = true;
      }
    }
    return nodesChanged;
  }

  private boolean isUnknown(Node n) {
    TargetValue value = latticeMap.get(n);
    return value == null || value.equals(TargetValue.getUnknown());
  }

  private boolean isConstant(Node n) {
    TargetValue value = latticeMap.get(n);
    return value != null && value.isConstant();
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
    if (isUnknown(left) || isUnknown(right)) {
      newValue = TargetValue.getUnknown();
    } else if (isConstant(left) && isConstant(right)) {
      TargetValue lhs = latticeMap.get(left);
      TargetValue rhs = latticeMap.get(right);
      newValue = operation.apply(lhs, rhs);
    } else {
      newValue = TargetValue.getBad();
    }
    previousValue = latticeMap.put(node, newValue);
    hasChanged = detectChange();
  }

  private boolean detectChange() {
    return (previousValue == null && !newValue.equals(TargetValue.getUnknown()))
        || (previousValue != null && !newValue.equals(previousValue));
  }

  @Override
  public void visit(Add node) {
    visitBinaryOperation((lhs, rhs) -> lhs.add(rhs), node, node.getLeft(), node.getRight());
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
    newValue = node.getTarval();
    previousValue = latticeMap.put(node, newValue);
    hasChanged = detectChange();
  }

  @Override
  public void visit(Conv node) {
    visitUnaryOperation(tv -> tv.convertTo(node.getMode()), node, node.getOp());
  }

  @Override
  public void visit(Div node) {
    visitBinaryOperation((lhs, rhs) -> lhs.div(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Minus node) {
    visitUnaryOperation(TargetValue::neg, node, node.getOp());
  }

  /**
   * @param operation Describes how to calculate the constant value of {@code node}, if {@code
   *     child} is constant. The input to this function is the constant value of {@code child}.
   * @param node {@link Minus}, {@link Not}, ...
   * @param child the only child of {@code node}
   */
  private void visitUnaryOperation(
      Function<TargetValue, TargetValue> operation, Node node, Node child) {
    if (isUnknown(child)) {
      newValue = TargetValue.getUnknown();
    } else if (isConstant(child)) {
      TargetValue value = latticeMap.get(child);
      newValue = operation.apply(value);
    } else {
      newValue = TargetValue.getBad();
    }
    previousValue = latticeMap.put(node, newValue);
    hasChanged = detectChange();
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
  public void visit(Not node) {
    visitUnaryOperation(TargetValue::not, node, node.getOp());
  }

  @Override
  public void visit(Or node) {
    visitBinaryOperation((lhs, rhs) -> lhs.or(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Phi node) {
    phiStrategy.visit(node);
  }

  @Override
  public void visit(Proj node) {
    visitUnaryOperation(Function.identity(), node, node.getPred());
  }

  @Override
  public void visit(Shl node) {
    visitBinaryOperation((lhs, rhs) -> lhs.shl(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Shr node) {
    visitBinaryOperation((lhs, rhs) -> lhs.shr(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Shrs node) {
    visitBinaryOperation((lhs, rhs) -> lhs.shrs(rhs), node, node.getLeft(), node.getRight());
  }

  @Override
  public void visit(Sub node) {
    visitBinaryOperation((lhs, rhs) -> lhs.sub(rhs), node, node.getLeft(), node.getRight());
  }

  private class SupremumStrategy extends DefaultNodeVisitor {
    @Override
    public void visit(Phi node) {
      newValue =
          StreamSupport.stream(node.getPreds().spliterator(), false)
              .map(n -> latticeMap.getOrDefault(n, TargetValue.getUnknown()))
              .reduce(TargetValue.getUnknown(), (a, b) -> supremum(a, b));
      previousValue = latticeMap.put(node, newValue);
      hasChanged = detectChange();
    }
  }

  private static TargetValue supremum(TargetValue a, TargetValue b) {
    if (a.equals(b)) {
      return a; // or b
    }
    if (a.equals(TargetValue.getUnknown())) {
      return b;
    }
    if (b.equals(TargetValue.getUnknown())) {
      return a;
    }
    return TargetValue.getBad();
  }

  private class EqualAndConstantStrategy extends DefaultNodeVisitor {
    @Override
    public void visit(Phi node) {
      Node first = Iterables.getFirst(node.getPreds(), null);
      if (first == null) {
        // Phi node has no inputs
        newValue = TargetValue.getBad();
      } else {
        // Phi node has at least 'first' as input
        TargetValue firstValue = latticeMap.getOrDefault(first, TargetValue.getUnknown());
        boolean allInputsAreEqual =
            StreamSupport.stream(node.getPreds().spliterator(), false)
                .skip(1)
                .map(n -> latticeMap.getOrDefault(n, TargetValue.getUnknown()))
                .allMatch(value -> value.equals(firstValue));
        if (allInputsAreEqual && firstValue.isConstant()) {
          newValue = firstValue;
        } else {
          newValue = TargetValue.getBad();
        }
      }
      previousValue = latticeMap.put(node, newValue);
      hasChanged = detectChange();
    }
  }
}
