package minijava.ir.optimize;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.Iterables;
import firm.*;
import firm.bindings.binding_irgopt;
import firm.nodes.*;
import java.util.ArrayList;
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
      if (!node.getBlock().equals(node.getSelector().getBlock())) {
        // The actual match and the phi are in different blocks, so we can't perform this
        // transformation.
        return;
      }
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
      determineProjectionNodes(node);
      int n = phi.getPredCount();
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
          Dump.dumpGraph(graph, "before-reach-def");
          System.out.println("Current block: " + currentBlock);
          System.out.println("Target block: " + newTarget);
          List<Phi> currentPhis = getPhiNodes(currentBlock);
          System.out.println("currentPhis: " + Iterables.toString(currentPhis));
          for (Phi currentPhi : currentPhis) {
            System.out.println("current Phi: " + currentPhi);
            for (BackEdges.Edge ref : BackEdges.getOuts(currentPhi)) {
              System.out.println("used in: " + ref.node);
              Node joinedDefinition =
                  joinReachableDefinitions(
                      currentPhi, currentPhi.getPred(i), (Block) ref.node.getBlock());
              if (ref.node instanceof Phi && joinedDefinition instanceof Phi) {
                // joinReachableDefinitions will always return a filled Phi node if there are multiple preds.
                // We only need one entry of that.
                Phi newPhi = (Phi) joinedDefinition;

                assert newPhi.getBlock() == ref.node.getBlock();
                ref.node.setPred(ref.pos, newPhi.getPred(ref.pos));
              } else {
                ref.node.setPred(ref.pos, joinedDefinition);
              }
            }
          }

          // Finally we delete the stale pointers to the projs/jmps we just redirected
          // We redirect by replacing predecessor nodes by a BAD.
          Node bad = graph.newBad(Mode.getANY());
          System.out.println("slfdkj: " + phi);
          phi.setPred(i, bad);
          currentBlock.setPred(i, bad);

          Dump.dumpGraph(graph, "after-reach-def");
        }
      }
    }
    BackEdges.disable(graph);
    binding_irgopt.remove_unreachable_code(
        graph.ptr); // find and replace unreachable code with Bad nodes
    binding_irgopt.remove_bads(graph.ptr); // find and replace unreachable code with Bad nodes
    BackEdges.enable(graph);
  }

  private Node joinReachableDefinitions(Node preferredDef, Node dominatingDefinition, Block usage) {
    System.out.println(
        "Trying to find " + preferredDef + ", defaulting to " + dominatingDefinition);
    assert preferredDef.getMode() == dominatingDefinition.getMode();
    assert Dominance.dominates(
        (Block) dominatingDefinition.getBlock(), usage); // Otherwise we might never terminate

    if (Dominance.dominates((Block) preferredDef.getBlock(), usage)) {
      // hooray, we found our preferred definition!
      System.out.println("Found " + preferredDef + " for " + usage);
      return preferredDef;
    }

    if (dominatingDefinition.getBlock().equals(usage)) {
      // We followed edges until we found the dominating definition, which is consequently deemed visible.
      System.out.println("Reached dominating definition " + dominatingDefinition);
      return dominatingDefinition;
    }

    // Otherwise, we have to ask our predecessors and join results with Phi
    int n = usage.getPredCount();
    assert n > 0;
    Node[] preds = new Node[n];
    boolean allSame = true;
    for (int i = 0; i < n; ++i) {
      preds[i] =
          joinReachableDefinitions(
              preferredDef, dominatingDefinition, (Block) usage.getPred(i).getBlock());
      if (i > 0) {
        allSame &= preds[0].equals(preds[i]);
      }
    }

    if (allSame) {
      // We don't need to create a new phi node for this one
      System.out.println("Forwarding allSame definition " + preds[0]);
      return preds[0];
    }

    // TODO: Pass a hashmap for memoization of nodes. This can blow up quadratically otherwise, I think.
    Node newPhi = graph.newPhi(usage, preds, preferredDef.getMode());
    System.out.println("Combined definitions into " + newPhi);
    return newPhi;
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
