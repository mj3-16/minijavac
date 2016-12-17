package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import firm.BackEdges;
import firm.Graph;
import firm.Mode;
import firm.TargetValue;
import firm.nodes.*;
import java.util.ArrayList;
import java.util.List;
import minijava.ir.Dominance;
import minijava.ir.NodeUtils;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;

/**
 * Performs a rather complex transformation on Cond nodes which match on Phi nodes with constant
 * inputs. Consider the case where we select on a Phi node, of which some of the operands are Const,
 * e.g.
 *
 * <p>Cond(Phi(Add(..., ...), Const(true), Const(false))
 *
 * <p>Now, we can just redirect incoming edges of the block with index 1 and 2 to their respective
 * jump targets of the cond projection nodes, since we know which branch will be taken.
 *
 * <p>We can't use the same simple scheme for Phi nodes as we do for Const nodes in
 * ConstantControlFlowOptimizer, since we can only determine constness *based on control flow*, e.g.
 * from which incoming edge we entered the current block.
 */
public class PhiBElimination extends BaseOptimizer {
  @Override
  public boolean optimize(Graph graph) {
    this.graph = graph;
    this.hasChanged = false;
    BackEdges.enable(graph);
    List<Node> l = new ArrayList<>();
    graph.walkTopological(new ConsumingNodeVisitor(l::add));
    for (Node n : l) {
      n.accept(this);
      if (hasChanged) {
        // We can only do one change per iteration, since we change control flow and the very Conds we operate on.
        // Otherwise we might handle inconsistent or deleted nodes.
        break;
      }
    }
    BackEdges.disable(graph);
    return hasChanged;
  }

  @Override
  public void visit(Cond node) {
    if (node.getSelector() instanceof Phi) {
      if (!node.getBlock().equals(node.getSelector().getBlock())) {
        // The actual match and the phi are in different blocks, so we can't perform this
        // transformation.
        return;
      }

      redirectConstantControlFlow(node);
    }
  }

  private void redirectConstantControlFlow(Cond node) {
    Block currentBlock = (Block) node.getBlock();
    Phi phi = (Phi) node.getSelector();
    //System.out.println(currentBlock + ": " + node + ", " + phi);
    int n = phi.getPredCount();
    NodeUtils.CondProjs projs = NodeUtils.determineProjectionNodes(node);
    for (int i = 0; i < n; i++) {
      Node pred = phi.getPred(i);
      if (pred instanceof Const && !(phi.getPred(i) instanceof Bad)) {
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
        Node proj =
            condition.getTarval().equals(TargetValue.getBTrue()) ? projs.true_ : projs.false_;
        BackEdges.Edge succ = BackEdges.getOuts(proj).iterator().next();
        // target is the block we actually want to redirect the jump to (from source).
        Block target = (Block) succ.node;

        // 1. Replace the old target block by a new one with the added incoming edge

        Node[] newPreds = seq(target.getPreds()).append(source).toArray(Node[]::new);
        Block newTarget = (Block) graph.newBlock(newPreds);
        Graph.exchange(target, newTarget);

        // That's actually all that the transformation does. The rest just has to perform cleanup
        // based on the control flow changes we just did, which is really nasty.

        // 2. Add preds to the phi nodes in the target block

        // There might be some Phis in target which need fixing, because we will add another predecessor (namely source).
        // For the new Phi entry, we will just copy the pred at index succ.pos, which is exactly the index of the
        // incoming control flow from currentBlock.
        List<Phi> fixUp = getPhiNodes(newTarget);

        // The predecessor index of the currentBlock/proj wrt. to the target Block.
        int predIdx = succ.pos;
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

        // 3. Potentially add new Phi nodes because the original definitions might not be visible any more

        // Changing the control flow also changed dominance frontiers, so that we potentially need to insert new
        // Phi nodes into target and dominated blocks, because otherwise some references might not have
        // dominating definitions.
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
        // Now, node 4 was dominated by 3, but isn't any longer dominated by 2 on the right!
        // This is problematic if 4 used any phi nodes of 3. So we have to move the respective phi nodes
        // into 4 (which is now the dominant frontier it belongs to) and also update all usages.

        // node 3 = currentBlock
        // node 1 = source
        // node 4 = target

        // For every Phi in currentBlock, check its usages and insert new Phi nodes as necessary.
        //Dump.dumpGraph(graph, "before-reach-def");
        //System.out.println("Target block: " + newTarget);
        List<Phi> currentPhis = getPhiNodes(currentBlock);
        //System.out.println("currentPhis: " + Iterables.toString(currentPhis));
        for (Phi currentPhi : currentPhis) {
          //System.out.println("Current Phi: " + currentPhi);
          for (BackEdges.Edge ref : BackEdges.getOuts(currentPhi)) {
            // So ref.node uses currentPhi as an input, but the definition of currentPhi might not dominate
            // ref.node.getBlock(). This is transparently handled in joinReachableDefinitions.
            //System.out.println("  used in: " + ref.node + ", input " + ref.pos);
            Block usage = (Block) ref.node.getBlock();
            if (ref.node instanceof Phi) {
              // In this case, it's enough to pretend the usage is in block usage.getPred(ref.pos)!
              // This is because Phis are the only means to join control flow and consequently visible definitions.
              usage = (Block) usage.getPred(ref.pos).getBlock();
            }
            Node joinedDefinition =
                joinReachableDefinitions(
                    currentPhi, currentPhi.getPred(i), usage, HashTreePSet.empty());
            ref.node.setPred(ref.pos, joinedDefinition);
          }
        }

        // 4. Delete incoming edge from the currentBlock, which we redirected

        // Finally we delete the stale pointers to the projs/jmps we just redirected
        // We redirect by replacing predecessor nodes by a BAD.
        Node bad = graph.newBad(Mode.getANY());
        phi.setPred(i, bad);
        currentBlock.setPred(i, bad);

        //Dump.dumpGraph(graph, "after-reach-def");
      }
    }
  }

  /**
   * Returns a definition for {@param preferredDef} that is actually visible at {@param usage},
   * defaulting to {@param defaultDefinition} where it is not.
   */
  private Node joinReachableDefinitions(
      Node preferredDef, Node defaultDefinition, Block usage, PSet<Block> loopBreakers) {
    //System.out.println(
    //    "    Trying to find "
    //        + preferredDef
    //        + ", defaulting to "
    //        + defaultDefinition
    //        + ", from usage in "
    //        + usage);
    assert preferredDef.getMode() == defaultDefinition.getMode();

    if (Dominance.dominates((Block) preferredDef.getBlock(), usage)) {
      // hooray, we found our preferred definition!
      //System.out.println("    Found " + preferredDef + " for " + usage);
      return preferredDef;
    }

    if (defaultDefinition.getBlock().equals(usage)) {
      // We followed edges until we found the default definition, which is consequently deemed visible.
      //System.out.println("    Reached default definition " + defaultDefinition);
      return defaultDefinition;
    }

    if (loopBreakers.contains(usage)) {
      // We follow some kind of endless loop. Just default to the defaultDefinition
      // and pray that it's visible (e.g. dominates the usage).
      assert Dominance.dominates((Block) defaultDefinition.getBlock(), usage);
      //System.out.println(
      //    "    Loop detected. Defaulting to default definition " + defaultDefinition);
      return defaultDefinition;
    }

    // Otherwise, we have to ask our predecessors and join results with Phi
    int n = usage.getPredCount();
    assert n > 0;
    Node[] preds = new Node[n];
    boolean allSame = true;
    for (int i = 0; i < n; ++i) {
      preds[i] =
          joinReachableDefinitions(
              preferredDef,
              defaultDefinition,
              (Block) usage.getPred(i).getBlock(),
              loopBreakers.plus(usage));
      if (i > 0) {
        allSame &= preds[0].equals(preds[i]);
      }
    }

    if (allSame) {
      // We don't need to create a new phi node for this one
      //System.out.println("    Forwarding allSame definition " + preds[0]);
      return preds[0];
    }

    // TODO: Possibly pass a hashmap for memoization of nodes.
    // This can blow up quadratically otherwise, I think.
    Node newPhi = graph.newPhi(usage, preds, preferredDef.getMode());
    //System.out.println("    Combined definitions into " + newPhi + " of block " + usage);
    return newPhi;
  }

  private static List<Phi> getPhiNodes(Block block) {
    return seq(BackEdges.getOuts(block)).map(be -> be.node).ofType(Phi.class).toList();
  }
}
