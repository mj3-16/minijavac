package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import firm.*;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;
import minijava.ir.PhiNodes;

/**
 * Replaces {@link Cond} nodes (or more precisely, their accompanying {@link Proj} nodes) with
 * {@link Jmp} nodes, if the condition is constant.
 *
 * <p>The {@link Proj} node that is no longer relevant is replaced with a {@link Bad} node. A
 * subsequent run of an {@link Optimizer} that removes such nodes is required.
 */
public class ConstantControlFlowOptimizer extends NodeVisitor.Default implements Optimizer {

  private Graph graph;
  private Proj trueProj;
  private Proj falseProj;
  private boolean hasChanged;

  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    hasChanged = false;
    PhiNodes.enable(graph);
    BackEdges.enable(graph);
    List<Node> topologicOrder = new ArrayList<>();
    graph.walkTopological(new ConsumingNodeVisitor(topologicOrder::add));
    topologicOrder.forEach(n -> n.accept(this));
    BackEdges.disable(graph);
    PhiNodes.disable(graph);
    return hasChanged;
  }

  @Override
  public void visit(Cond node) {
    if (node.getSelector() instanceof Const) {
      TargetValue condition = ((Const) node.getSelector()).getTarval();
      determineProjectionNodes(node);
      hasChanged = true;
      if (condition.equals(TargetValue.getBTrue())) {
        Graph.exchange(trueProj, graph.newJmp(node.getBlock()));
        Graph.exchange(falseProj, graph.newBad(Mode.getANY()));
      } else {
        Graph.exchange(falseProj, graph.newJmp(node.getBlock()));
        Graph.exchange(trueProj, graph.newBad(Mode.getANY()));
      }
    } else if (node.getSelector() instanceof Phi) {
      // Consider the case where we select on a Phi node, of which some of the operands are Const, e.g.
      //
      //  Cond(Phi(Add(..., ...), Const(true), Const(false))
      //
      // Now, we can just redirect incoming edges of the block with index 1 and 2 to their respective jump targets
      // of the cond projection nodes, since we know which branch will be taken.
      //
      // We could use the same scheme for the special case where the selected node is Const (above). This would not
      // produce unnecessary jmps, at the cost of having to handle the start block specially. That's why we don't merge
      // both cases.
      // Also, we can't use the same simple scheme for Phi nodes as we do for Const nodes, since we can only determine
      // constness *based on control flow*, e.g. from which incoming edge we entered the current block.
      Block currentBlock = (Block) node.getBlock();
      Phi phi = (Phi) node.getSelector();
      determineProjectionNodes(node);
      int n = phi.getPredCount();
      for (int i = 0; i < n; i++) {
        Node pred = phi.getPred(i);
        if (pred instanceof Const && !(currentBlock.getPred(i) instanceof Bad)) {
          // Success, we can redirect the i'th incoming jmp!
          // The above check is a sufficient condition for this: We need the Phi pred to be const, as well as
          // check that the predeccesor node of the current block isn't BAD, which may well happen if already exchanged
          // it in this traversal (sigh).
          // We redirect by replacing predecessor nodes by a BAD.
          Const condition = (Const) pred;
          Node bad = graph.newBad(Mode.getANY());
          // source is the instruction from which we redirect the jump
          // This can potentially be a Proj[X] or a Jmp
          Node source = currentBlock.getPred(i);
          phi.setPred(i, bad);
          currentBlock.setPred(i, bad);
          Node proj = condition.getTarval().equals(TargetValue.getBTrue()) ? trueProj : falseProj;
          BackEdges.Edge succ = BackEdges.getOuts(proj).iterator().next();
          Block target = (Block) succ.node;
          int predIdx = succ.pos;
          Node[] newPreds = seq(target.getPreds()).append(source).toArray(Node[]::new);
          Block newTarget = (Block) graph.newBlock(newPreds);
          List<Phi> fixUp = Lists.newArrayList(PhiNodes.getPhiNodes(target));
          Graph.exchange(target, newTarget);
          System.out.println(Iterables.toString(PhiNodes.getPhiNodes(newTarget)));
          // TODO: phi nodes?!
          for (Phi targetPhi : fixUp) {
            System.out.println(targetPhi);
            System.out.println(Iterables.toString(targetPhi.getPreds()));
            Node[] newPhiPreds =
                seq(targetPhi.getPreds()).append(targetPhi.getPred(predIdx)).toArray(Node[]::new);
            System.out.println(Arrays.toString(newPhiPreds));
            Phi newPhi = (Phi) graph.newPhi(newTarget, newPhiPreds, targetPhi.getMode());
            newPhi.setLoop(targetPhi.getLoop());
            Graph.exchange(targetPhi, newPhi);
          }
          hasChanged = true;
        }
      }
    }
    Dump.dumpGraph(graph, "sdflksjfdl");
  }

  private static Node getSucc(Node n) {
    assert BackEdges.getNOuts(n) == 1;
    return BackEdges.getOuts(n).iterator().next().node;
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
