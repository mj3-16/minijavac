package minijava.ir.assembler;

import firm.Graph;
import firm.nodes.*;
import java.util.*;
import minijava.ir.assembler.instructions.Argument;
import minijava.ir.assembler.instructions.ConstArgument;
import minijava.ir.assembler.location.Location;
import minijava.ir.assembler.location.Register;
import minijava.ir.assembler.location.StackLocation;
import minijava.ir.utils.MethodInformation;

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
  private MethodInformation info;

  @Override
  public void process(Graph graph) {
    this.graph = graph;
    this.info = new MethodInformation(graph);
    this.assignedStackSlots = new HashMap<>();
  }

  @Override
  public Location getLocation(Node node) {
    if (node instanceof Proj) {
      Proj proj = (Proj) node;
      if (proj.getPred().equals(graph.getArgs())) {
        // the proj node points to a method argument
        int slot = proj.getNum();
        // this seems to be correct?
        return new StackLocation(Register.BASE_POINTER, (slot + 2) * STACK_SLOT_SIZE);
      } else if (proj.getPred().getPredCount() > 0 && proj.getPred().getPred(0) instanceof Call) {
        // the proj node points to method result
        // the result always lays in the RAX register
        // [Call] <- [Tuple Proj] <- [Is Proj]
        return getStackSlotAsLocation(proj.getPred().getPred(0));
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
      start = 2;
    } else if (node instanceof Return) {
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
    return args;
  }

  @Override
  public Location getResultLocation(Node node) {
    if (node instanceof Conv) {
      // ignore Conv nodes
      return getLocation(node.getPred(0));
    }
    return getLocation(node);
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

  @Override
  public String getActivationRecordInfo() {
    List<String> lines = new ArrayList<>();
    Map<Integer, Node> nodesForAssignedSlot = new HashMap<>();
    for (Node node : assignedStackSlots.keySet()) {
      nodesForAssignedSlot.put(assignedStackSlots.get(node), node);
    }
    for (int i = info.paramNumber - 1; i > 0; i--) {
      String slotInfo = String.format("%3d[%3d(%%esp)]", i + 1, (i + 1) * STACK_SLOT_SIZE);
      if (i == 1) {
        lines.add(slotInfo + ": this");
      } else {
        lines.add(slotInfo + ": argument " + (i - 1));
      }
    }
    for (int slot = 0; slot < currentSlotNumber; slot++) {
      String slotInfo = String.format("%3d[%3d(%%ebp)]", slot, -slot * STACK_SLOT_SIZE);
      if (nodesForAssignedSlot.containsKey(slot)) {
        slotInfo += ": " + getInfoStringForNode(nodesForAssignedSlot.get(slot));
      }
      lines.add(slotInfo);
    }
    return String.join(System.lineSeparator(), lines);
  }

  private String getInfoStringForNode(Node node) {
    return node.toString();
  }
}
