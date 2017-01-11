package minijava.ir;

import static firm.bindings.binding_irnode.ir_opcode.iro_Const;
import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.nodes.Cond;
import firm.nodes.Const;
import firm.nodes.Node;
import firm.nodes.Proj;
import java.util.Optional;

/** For lack of a better name */
public class NodeUtils {
  public static Optional<Const> asConst(Node node) {
    return node.getOpCode().equals(iro_Const) ? Optional.of((Const) node) : Optional.empty();
  }

  public static CondProjs determineProjectionNodes(Cond node) {
    Proj[] projs =
        seq(BackEdges.getOuts(node)).map(be -> be.node).ofType(Proj.class).toArray(Proj[]::new);
    assert projs.length == 2;
    if (projs[0].getNum() == Cond.pnTrue) {
      return new CondProjs(projs[0], projs[1]);
    } else {
      return new CondProjs(projs[1], projs[0]);
    }
  }

  public static class CondProjs {
    public final Proj true_;
    public final Proj false_;

    CondProjs(Proj true_, Proj false_) {
      this.true_ = true_;
      this.false_ = false_;
    }
  }
}
