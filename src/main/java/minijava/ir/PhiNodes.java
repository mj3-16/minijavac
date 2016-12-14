package minijava.ir;

import static firm.bindings.binding_irgraph.ir_resources_t.IR_RESOURCE_PHI_LIST;

import com.sun.jna.Pointer;
import firm.Graph;
import firm.bindings.binding_irgraph;
import firm.bindings.binding_irnode;
import firm.nodes.Block;
import firm.nodes.Phi;
import java.util.Iterator;

public class PhiNodes {
  public static void enable(Graph graph) {
    binding_irgraph.ir_reserve_resources(graph.ptr, IR_RESOURCE_PHI_LIST.val);
  }

  public static void disable(Graph graph) {
    binding_irgraph.ir_free_resources(graph.ptr, IR_RESOURCE_PHI_LIST.val);
  }

  public static Iterable<Phi> getPhiNodes(Block block) {
    return () ->
        new Iterator<Phi>() {
          Pointer current = null;
          Pointer next = binding_irnode.get_Block_phis(block.ptr);

          @Override
          public boolean hasNext() {
            return next != null;
          }

          @Override
          public Phi next() {
            current = next;
            next = binding_irnode.get_Phi_next(current);
            return new Phi(current);
          }
        };
  }
}
