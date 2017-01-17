package minijava.ir.emit;

import firm.nodes.Node;
import firm.nodes.Proj;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;

class ExpressionIR {

  private final boolean isControlFlow;
  private final Node asNode;
  private final ControlFlowProjs asControlFlow;

  private ExpressionIR(boolean isControlFlow, Node asNode, ControlFlowProjs asControlFlow) {
    this.isControlFlow = isControlFlow;
    this.asNode = asNode;
    this.asControlFlow = asControlFlow;
  }

  public boolean isControlFlow() {
    return isControlFlow;
  }

  public boolean isValue() {
    return !isControlFlow;
  }

  public Node asValue() {
    assert isValue();
    return asNode;
  }

  public ControlFlowProjs asControlFlow() {
    assert isControlFlow();
    return asControlFlow;
  }

  public static ExpressionIR fromValue(Node node) {
    return new ExpressionIR(false, node, null);
  }

  public static ExpressionIR fromControlFlow(Proj trueJmp, Proj falseJmp) {
    return fromControlFlow(HashTreePSet.singleton(trueJmp), HashTreePSet.singleton(falseJmp));
  }

  public static ExpressionIR fromControlFlow(PSet<Proj> trueJmp, PSet<Proj> falseJmp) {
    return new ExpressionIR(true, null, new ControlFlowProjs(trueJmp, falseJmp));
  }

  public static ExpressionIR fromControlFlow(ControlFlowProjs controlFlow) {
    return new ExpressionIR(true, null, controlFlow);
  }
}
