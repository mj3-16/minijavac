package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.*;
import java.util.*;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.instructions.ConstArgument;
import minijava.ir.assembler.location.Location;
import minijava.ir.assembler.location.Register;
import minijava.ir.assembler.location.StackLocation;

/**
 * Simple stack based node allocator that tries to implement the scheme from the compiler lab
 * slides.
 *
 * <p>Every value is stored on the stack in a new slot thereby using lot's of memory. The advantage
 * of this inefficiency is the simplicity of the implementation.
 */
public class SimpleNodeAllocator implements NodeAllocator {

  private static final int STACK_SLOT_SIZE = 8; // we're on an 64bit system
  private Map<Node, Integer> assignedStackSlots;
  private int currentSlotNumber = 0;
  private Graph graph;

  @Override
  public void process(Graph graph) {
    this.graph = graph;
    this.assignedStackSlots = new HashMap<>();
  }

  @Override
  public Location getLocation(Node node) {
    if (node instanceof Proj) {
      Proj proj = (Proj) node;
      if (proj.getPred() == graph.getArgs()) {
        // the proj node points to a method argument
        int slot = proj.getNum();
        // TODO: is this correct?
        return new StackLocation(Register.BASE_POINTER, (slot + 1) * STACK_SLOT_SIZE);
      } else if (proj.getPred() instanceof Call) {
        // the proj node points to method result
        // the result always lays on the stack
        return new StackLocation(Register.STACK_POINTER, 0);
      }
    } else if (node instanceof Conv) {
      // ignore Conv nodes
      return getLocation(node.getPred(0));
    }
    return getStackSlotAsLocation(node);
  }

  @Override
  public List<Argument> getArguments(Node node) {
    List<Argument> args = new ArrayList<>();
    int start = 0;
    if (node instanceof Call) {
      start = 1;
    }
    for (int i = start; i < node.getPredCount(); i++) {
      Node argNode = node.getPred(i);
      if (argNode instanceof Const) {
        args.add(new ConstArgument(((Const) argNode).getTarval().asInt()));
      } else {
        args.add(getLocation(argNode));
      }
    }
    for (Node argNodes : node.getPreds()) {}

    return args;
  }

  @Override
  public Location getResultLocation(Node node) {
    if (node instanceof Conv) {
      // ignore Conv nodes
      return getLocation(node.getPred(0));
    } /*else if (node instanceof Call){
        currentSlotNumber += node.getPredCount() - 2;
      }*/
    return getStackSlotAsLocation(node);
  }

  @Override
  public int getActivationRecordSize() {
    return (currentSlotNumber + 1) * STACK_SLOT_SIZE;
  }

  private int getStackSlotOffset(Node node) {
    return assignedStackSlots.get(node) * STACK_SLOT_SIZE;
  }

  private void assignStackSlot(Node node) {
    assignedStackSlots.put(node, currentSlotNumber++);
  }

  private Location getStackSlotAsLocation(Node node) {
    if (!assignedStackSlots.containsKey(node)) {
      assignStackSlot(node);
    }
    return new StackLocation(Register.BASE_POINTER, -getStackSlotOffset(node));
  }
}
