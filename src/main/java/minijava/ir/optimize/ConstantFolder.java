package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableSet;
import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import minijava.ir.utils.FirmUtils;
import minijava.ir.utils.GraphUtils;

public class ConstantFolder extends BaseOptimizer {

  private static final Set<Mode> HANDLED_MODES =
      ImmutableSet.of(Mode.getBu(), Mode.getb(), Mode.getIs(), Mode.getLs());
  private Map<Node, TargetValue> latticeMap;

  @Override
  public boolean optimize(Graph graph) {
    this.latticeMap = new HashMap<>();
    this.graph = graph;
    return FirmUtils.withBackEdges(
        graph,
        () -> {
          do {
            hasChanged = false;
            GraphUtils.walkPostOrder(graph, this::visit);
          } while (hasChanged);
          return replaceConstants();
        });
  }

  private boolean replaceConstants() {
    boolean nodesChanged = false;
    for (Entry<Node, TargetValue> e : latticeMap.entrySet()) {
      if (!(e.getKey() instanceof Const) && e.getValue().isConstant()) {
        redirectMem(e.getKey());
        Node constant = graph.newConst(e.getValue());
        Graph.exchange(e.getKey(), constant);
        nodesChanged = true;
      }
    }
    return nodesChanged;
  }

  /**
   * Div and Mod have side effects (e.g. division by zero) which are modeled with memory edges. When
   * constant fold these, we also have to redirect the memory edges.
   */
  private void redirectMem(Node node) {
    Optional<Node> mem = getInputMem(node);
    Optional<Proj> memProj = getOutputMemProj(node);
    if (mem.isPresent() && memProj.isPresent()) {
      for (BackEdges.Edge be : BackEdges.getOuts(memProj.get())) {
        be.node.setPred(be.pos, mem.get());
      }
    }
  }

  private Optional<Node> getInputMem(Node node) {
    switch (node.getOpCode()) {
      case iro_Div:
        return Optional.of(((Div) node).getMem());
      case iro_Mod:
        return Optional.of(((Mod) node).getMem());
      default:
        return Optional.empty();
    }
  }

  private Optional<Proj> getOutputMemProj(Node node) {
    return seq(BackEdges.getOuts(node))
        .map(be -> be.node)
        .filter(n -> n.getMode().equals(Mode.getM()))
        .ofType(Proj.class)
        .findFirst();
  }

  private TargetValue getValue(Node n) {
    return latticeMap.getOrDefault(n, TargetValue.getUnknown());
  }

  private boolean isUnknown(Node n) {
    return getValue(n).equals(TargetValue.getUnknown());
  }

  private boolean isConstant(Node n) {
    return getValue(n).isConstant();
  }

  private boolean isBad(Node n) {
    return getValue(n).equals(TargetValue.getBad());
  }

  /**
   * @param operation Describes how to calculate the constant value of {@code node}, if both {@code
   *     left} and {@code right} are constant. The inputs to this function are the constant values
   *     of {@code left} and {@code right}.
   * @param node {@link Add}, {@link And}, {@link Mul}, ...
   * @param left the left child of {@code node}
   * @param right the right child of {@code node}
   */
  private void visitBinaryOperation(
      BiFunction<TargetValue, TargetValue, TargetValue> operation,
      Node node,
      Node left,
      Node right) {
    visitBinaryOperationOptional(operation.andThen(Optional::of), node, left, right);
  }

  /**
   * A more specific overload, the {@param operation} of which also allows to return
   * Optional.empty() in case the result could not be calculated (e.g. division by zero).
   */
  private void visitBinaryOperationOptional(
      BiFunction<TargetValue, TargetValue, Optional<TargetValue>> operation,
      Node node,
      Node left,
      Node right) {
    if (isBad(left) || isBad(right)) {
      updateValue(node, TargetValue.getBad());
    } else if (isUnknown(left) || isUnknown(right)) {
      updateValue(node, TargetValue.getUnknown());
    } else {
      assert isConstant(left) && isConstant(right);
      TargetValue lhs = getValue(left);
      TargetValue rhs = getValue(right);
      updateValue(node, operation.apply(lhs, rhs).orElse(TargetValue.getBad()));
    }
  }

  @Override
  public void defaultVisit(Node n) {
    updateValue(n, TargetValue.getBad());
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
    updateValue(node, node.getTarval());
  }

  @Override
  public void visit(Conv node) {
    visitUnaryOperation(tv -> tv.convertTo(node.getMode()), node, node.getOp());
  }

  @Override
  public void visit(Div node) {
    visitBinaryOperationOptional(
        (lhs, rhs) -> {
          if (rhs.isNull()) {
            return Optional.empty();
          }
          return Optional.of(lhs.div(rhs));
        },
        node,
        node.getLeft(),
        node.getRight());
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
    if (isBad(child)) {
      updateValue(node, TargetValue.getBad());
    } else if (isUnknown(child)) {
      updateValue(node, TargetValue.getUnknown());
    } else if (isConstant(child)) {
      updateValue(node, operation.apply(getValue(child)));
    }
  }

  private void updateValue(Node node, TargetValue newValue) {
    TargetValue previousValue = latticeMap.put(node, newValue);
    if (previousValue == null) {
      previousValue = TargetValue.getUnknown();
    }
    hasChanged |= !newValue.equals(previousValue);
  }

  @Override
  public void visit(Mod node) {
    visitBinaryOperationOptional(
        (lhs, rhs) -> {
          if (rhs.isNull()) {
            return Optional.empty();
          }
          return Optional.of(lhs.mod(rhs));
        },
        node,
        node.getLeft(),
        node.getRight());
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
    TargetValue newValue =
        seq(node.getPreds())
            .map(this::getValue)
            .reduce(TargetValue.getUnknown(), ConstantFolder::supremum);
    updateValue(node, newValue);
  }

  @Override
  public void visit(Proj node) {
    if (HANDLED_MODES.contains(node.getMode())) {
      visitUnaryOperation(Function.identity(), node, node.getPred());
    } else {
      updateValue(node, TargetValue.getBad());
    }
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
}
