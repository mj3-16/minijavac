package minijava.ir.optimize;

import firm.BackEdges;
import firm.Graph;
import firm.bindings.binding_irnode;
import firm.nodes.*;
import java.util.*;
import org.jooq.lambda.tuple.Tuple2;

public class AlgebraicSimplifier extends BaseOptimizer {

  private List<Tuple2<Node, Node>> nodesToExchange;

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    hasChanged = false;
    nodesToExchange = new ArrayList<>();
    BackEdges.enable(graph);
    fixedPointIteration();
    replaceNodes();
    BackEdges.disable(graph);
    return nodesToExchange.size() > 0;
  }

  @Override
  public void visit(Add node) {
    Optional<Tuple2<Const, Node>> subNodesOpt = split(node);
    if (!subNodesOpt.isPresent()) {
      return;
    }
    Tuple2<Const, Node> subNodes = subNodesOpt.get();
    if (subNodes.v1.getTarval().isNull()) { // x * 0  == 0
      exchange(node, subNodes.v2);
    } else if (subNodes.v2.getOpCode() == binding_irnode.ir_opcode.iro_Add) {
      // (x + 8) + 8 == x + 16
      Add subAddNode = (Add) subNodes.v2;
      Optional<Tuple2<Const, Node>> subAddNodesOpt = split(subAddNode);
      if (subAddNodesOpt.isPresent()) {
        Node constant =
            graph.newConst(subNodes.v1.getTarval().add(subAddNodesOpt.get().v1.getTarval()));
        exchange(node, graph.newAdd(node.getBlock(), subAddNodesOpt.get().v2, constant));
      }
    }
  }

  @Override
  public void visit(Mul node) {
    Optional<Tuple2<Const, Node>> subNodesOpt = split(node);
    if (!subNodesOpt.isPresent()) {
      return;
    }
    Tuple2<Const, Node> subNodes = subNodesOpt.get();
    if (subNodes.v1.getTarval().isNull()) { // x * 0  == 0
      exchange(node, subNodes.v1);
    } else if (subNodes.v1.getTarval().isOne()) { // x * 1 == x
      exchange(node, subNodes.v2);
    } else if (subNodes.v2.getOpCode() == binding_irnode.ir_opcode.iro_Mul) {
      // (x * 8) * 8 == x * 64
      Mul subMulNode = (Mul) subNodes.v2;
      Optional<Tuple2<Const, Node>> subMulNodesOpt = split(subMulNode);
      if (subMulNodesOpt.isPresent()) {
        Node constant =
            graph.newConst(subNodes.v1.getTarval().mul(subMulNodesOpt.get().v1.getTarval()));
        exchange(node, graph.newMul(node.getBlock(), subMulNode, constant));
      }
    }
  }

  @Override
  public void visit(Minus node) {
    if (node.getOp().getOpCode() == binding_irnode.ir_opcode.iro_Minus) {
      exchange(node, ((Minus) node.getOp()).getOp());
    }
  }

  private Optional<Tuple2<Const, Node>> split(Binop node) {
    Const constant;
    Node other;
    if (node.getLeft() instanceof Const) {
      constant = (Const) node.getLeft();
      other = node.getRight();
    } else if (node.getRight() instanceof Const) {
      constant = (Const) node.getRight();
      other = node.getLeft();
    } else {
      return Optional.empty();
    }
    return Optional.of(new Tuple2<Const, Node>(constant, other));
  }

  private void exchange(Node oldNode, Node newNode) {
    hasChanged = true;
    nodesToExchange.add(new Tuple2<Node, Node>(oldNode, newNode));
  }

  private void replaceNodes() {
    for (Tuple2<Node, Node> oldAndNew : nodesToExchange) {
      Graph.exchange(oldAndNew.v1, oldAndNew.v2);
    }
  }
}
