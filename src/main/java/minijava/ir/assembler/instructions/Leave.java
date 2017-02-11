package minijava.ir.assembler.instructions;

import java.util.ArrayList;

/**
 * Pseudo instruction later to be substituted with the typical function epilogue when the
 * ActivationRecord is clear.
 */
public class Leave extends Instruction {
  public Leave() {
    super(new ArrayList<>(), new ArrayList<>());
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
