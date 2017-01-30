package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import firm.Graph;
import firm.nodes.*;
import java.util.List;
import minijava.ir.utils.ExtensionalEqualityComparator;
import minijava.ir.utils.GraphUtils;

/**
 * Orders all operands to a commutative/(anti-)symmetric operator as prescribed by {@link
 * minijava.ir.utils.ExtensionalEqualityComparator}.
 */
public class ExpressionNormalizer extends BaseOptimizer {

  @Override
  public boolean optimize(Graph graph) {
    hasChanged = false;
    GraphUtils.topologicalOrder(graph).forEach(this::visit);
    return hasChanged;
  }

  @Override
  public void visit(Add node) {
    hasChanged |= reorderPreds(node);
  }

  @Override
  public void visit(Mul node) {
    hasChanged |= reorderPreds(node);
  }

  @Override
  public void visit(Cmp node) {
    boolean swapped = reorderPreds(node);
    if (swapped) {
      hasChanged = true;
      // we also have to change the relation if it wasn't symmetric.
      node.setRelation(node.getRelation().inversed());
    }
  }

  private static boolean reorderPreds(Node node) {
    List<Node> preds = seq(node.getPreds()).sorted(ExtensionalEqualityComparator.INSTANCE).toList();

    if (Iterables.elementsEqual(preds, node.getPreds())) {
      return false;
    }

    for (int i = 0; i < preds.size(); ++i) {
      node.setPred(i, preds.get(i));
    }
    return true;
  }
}
