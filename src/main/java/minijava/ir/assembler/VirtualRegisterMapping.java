package minijava.ir.assembler;

import firm.nodes.Node;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import minijava.ir.assembler.instructions.Instruction;
import minijava.ir.assembler.registers.VirtualRegister;

public class VirtualRegisterMapping {
  private final Map<Node, VirtualRegister> mapping = new HashMap<>();
  private final Map<VirtualRegister, Instruction> definitions = new HashMap<>();
  private final Set<Node> neverDefined = new HashSet<>();
  private int nextFreeId = 0;

  public boolean hasRegisterAssigned(Node node) {
    return mapping.containsKey(node);
  }

  public VirtualRegister registerForNode(Node node) {
    if (neverDefined.contains(node)) {
      throw new UndefinedNodeException(node);
    }
    return mapping.computeIfAbsent(node, d -> new VirtualRegister(nextFreeId++, d));
  }

  public void markNeverDefined(Node node) {
    neverDefined.add(node);
  }

  public void setDefinition(VirtualRegister register, Instruction definition) {
    Instruction oldDefinition = definitions.put(register, definition);
    if (oldDefinition != null) {
      throw new MultipleDefinitionException(register, oldDefinition, definition);
    }
  }

  public Instruction getDefinition(VirtualRegister register) {
    return definitions.get(register);
  }
}
