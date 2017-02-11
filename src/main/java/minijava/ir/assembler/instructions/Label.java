package minijava.ir.assembler.instructions;

import java.util.ArrayList;

/** Should only be present in the lowered form, e.g. not part of a higher-level code block. */
public class Label extends Instruction {
  public final String label;

  public Label(String label) {
    super(new ArrayList<>(), new ArrayList<>());
    this.label = label;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
