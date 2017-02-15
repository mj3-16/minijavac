package minijava.backend.selection;

import minijava.backend.instructions.Instruction;
import minijava.backend.registers.VirtualRegister;

public class MultipleDefinitionException extends RuntimeException {
  public MultipleDefinitionException(
      VirtualRegister register, Instruction oldDefinition, Instruction definition) {
    super(
        "Node value "
            + register.value
            + " in register with id "
            + register.id
            + " has multiple definitions. Previously: "
            + oldDefinition
            + " and now also "
            + definition);
  }
}
