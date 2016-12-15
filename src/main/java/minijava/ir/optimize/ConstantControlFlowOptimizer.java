package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import firm.*;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.StreamSupport;
import minijava.ir.Dominance;

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

    BackEdges.enable(graph);
    boolean changedAtLeastOnce = false;
    do {
      hasChanged = false;
      //graph.walkTopological(this);
      List<Node> l = new ArrayList<>();
      graph.walkTopological(new ConsumingNodeVisitor(l::add));
      l.forEach(n -> n.accept(this));
      changedAtLeastOnce |= hasChanged;
    } while (hasChanged);
    BackEdges.disable(graph);

    return changedAtLeastOnce;
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
      System.out.println(currentBlock + ": " + node + ", " + phi);
      if (phi.getNr() == 137) {
        Dump.dumpGraph(graph, "137");
      }
      determineProjectionNodes(node);
      int n = phi.getPredCount();
      for (int i = 0; i < n; i++) {
        Node pred = phi.getPred(i);
        if (pred instanceof Const && !(currentBlock.getPred(i) instanceof Bad)) {
          // Success, we can redirect the i'th incoming jmp!
          // The above check is a sufficient condition for this: We need the Phi pred to be const, as well as
          // check that the predeccesor node of the current block isn't BAD, which may well happen if already exchanged
          // it in this traversal (sigh).
          Const condition = (Const) pred;
          hasChanged = true;

          // source is the instruction from which we redirect the jump
          // This can potentially be a Proj[X] or a Jmp
          Node source = currentBlock.getPred(i);
          // proj is the jump pointing to the block we want to redirect to.
          // We know which jump to take here, because we know the constant value of the condition
          // for the specific currentBlock's predecessor i.
          Node proj = condition.getTarval().equals(TargetValue.getBTrue()) ? trueProj : falseProj;
          BackEdges.Edge succ = BackEdges.getOuts(proj).iterator().next();
          // target is the block we actually want to redirect the jump to (from source).
          Block target = (Block) succ.node;

          // Now, there might be some Phis in target which need fixing, because we will add another predecessor (namely source).
          // For the new Phi entry, we will just copy the pred at index succ.pos, which is exactly the index of the
          // incoming control flow from currentBlock.
          List<Phi> fixUp = getPhiNodes(target);

          Node[] newPreds = seq(target.getPreds()).append(source).toArray(Node[]::new);
          Block newTarget = (Block) graph.newBlock(newPreds);
          Graph.exchange(target, newTarget);

          int predIdx =
              succ.pos; // The predecessor index of the currentBlock/proj wrt. to the target Block.
          for (Phi targetPhi : fixUp) {
            // Make a 'deep' copy of targetPhi, with an added predecessor entry for the new predecessor.
            // entry will point to the same predecessor as the pred entry for currentBlock/proj does,
            // via looking up at predIdx.
            Node newPred = targetPhi.getPred(predIdx);
            if (newPred instanceof Phi && newPred.getBlock().equals(currentBlock)) {
              // In this case we can (and should) even be more precise, since we know
              // the exact value of the phi node from control flow.
              newPred = newPred.getPred(i);
            }
            Node[] newPhiPreds = seq(targetPhi.getPreds()).append(newPred).toArray(Node[]::new);
            Phi newPhi = (Phi) graph.newPhi(newTarget, newPhiPreds, targetPhi.getMode());
            newPhi.setLoop(targetPhi.getLoop());
            Graph.exchange(targetPhi, newPhi);
          }
          System.out.println(newTarget);

          // Changing the control flow also changed dominance frontiers, so that we potentially need to insert new
          // Phi nodes into target, because otherwise some references might not have dominating definitions.
          //
          // Consider this:
          //
          //  1 +-->+ 2            1 +-->+ (2 + 3)
          //    |  /                 |  /|
          //    | /                  | / |
          //    |/                   |/  |
          //    v                    v   |
          //  3 +-->+ 4            4 +   /
          //    |  /                 |  /
          //    | /                  | /
          //    |/                   |/
          //    v                    v
          //    + 5                  + 5
          //
          // Which is the situation for a simple if (true && true) statement without optimization.
          // We know that we can redirect edge 1-3 directly to 4 and pull node 3 into 2, like on the right.
          // Now, node 4 was dominated by 3, but isnt any longer dominated by 2 on the right!
          // This is problematic if 4 used any phi nodes of 3. So we have to move the respective phi nodes
          // into 4 (which is now the dominant frontier it belongs to) and also update all usages.

          // node 3 = currentBlock
          // node 1 = source
          // node 4 = target

          // 1. copy every Phi from node 3 to node 4, referencing the phi from node 3 as well as the input from node 1
          Dump.dumpGraph(graph, "blub");
          System.out.println("Current block: " + currentBlock);
          System.out.println("Target block: " + newTarget);
          System.out.println(Dominance.dominates(newTarget, currentBlock));
          System.out.println(Dominance.dominates(currentBlock, newTarget));
          List<Phi> currentPhis = getPhiNodes(currentBlock);
          System.out.println("currentPhis: " + Iterables.toString(currentPhis));
          for (Phi currentPhi : currentPhis) {
            System.out.println(currentPhi);
            List<BackEdges.Edge> dominatedRefs =
                seq(BackEdges.getOuts(currentPhi))
                    .filter(be -> Dominance.dominates(newTarget, (Block) be.node.getBlock()))
                    .toList();
            if (dominatedRefs.isEmpty()) continue;

            System.out.println(
                "Dominated refs: " + Iterables.toString(seq(dominatedRefs).map(be -> be.node)));
            System.out.println(
                "All refs:       "
                    + Iterables.toString(seq(BackEdges.getOuts(currentPhi)).map(be -> be.node)));

            // We have to update all dominated references with a (potentially new) phi node
            for (BackEdges.Edge dominatedRef : dominatedRefs) {
              if (dominatedRef.node instanceof Phi
                  && dominatedRef.node.getBlock().equals(newTarget)) {
                // This should already have been handled above.
                continue;
              }

              // In all other cases, we have to add a phi node to newTarget and redirect all refs.
              // All preds except the new one should point to the old phi.
              // The new should follow the value at currentPhi, analogous to the first Phi fixup loop
              System.out.println(dominatedRef.node);
              Node[] preds = new Node[newTarget.getPredCount()];
              Arrays.fill(preds, currentPhi);
              preds[preds.length - 1] = currentPhi.getPred(i); // This is why we are doing all this.
              System.out.println(Arrays.toString(preds));
              Phi newPhi = (Phi) graph.newPhi(newTarget, preds, currentPhi.getMode());
              newPhi.setLoop(currentPhi.getLoop());
              dominatedRef.node.setPred(dominatedRef.pos, newPhi);
            }
          }

          // Finally we delete the stale pointers to the projs/jmps we just redirected
          // We redirect by replacing predecessor nodes by a BAD.
          Node bad = graph.newBad(Mode.getANY());
          //phi.setPred(i, bad);
          currentBlock.setPred(i, bad);
        }
      }
    }
  }

  private static List<Phi> getPhiNodes(Block block) {
    return seq(BackEdges.getOuts(block)).map(be -> be.node).ofType(Phi.class).toList();
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
