package minijava.ir.assembler.instructions;

import java.util.ArrayList;

/**
 * Pseudo instruction later to be substituted with the typical function prologue when the
 * ActivationRecord is clear.
 */
public class Enter extends Instruction {
  public Enter() {
    super(new ArrayList<>(), new ArrayList<>());
  }
}
