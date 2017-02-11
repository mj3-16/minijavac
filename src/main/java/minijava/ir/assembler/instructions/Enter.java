package minijava.ir.assembler.instructions;

import java.util.ArrayList;

/**
 * Pseudo instruction later to be substituted with the typical function prologue when the size of
 * the local variable area is clear.
 */
public class Enter extends CodeBlockInstruction {
  public Enter() {
    super(new ArrayList<>(), new ArrayList<>());
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
