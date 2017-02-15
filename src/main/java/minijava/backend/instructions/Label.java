package minijava.backend.instructions;

import java.util.ArrayList;
import java.util.List;
import minijava.backend.block.PhiFunction;

/** Should only be present in the lowered form, e.g. not part of a higher-level code block. */
public class Label extends Instruction {
  public final String label;
  /**
   * As in the Linear Scan on SSA form paper, we attach phis to the label, as they are unordered.
   * Also, these Phis only reference physical registers.
   */
  public final List<PhiFunction> physicalPhis;

  public Label(String label, List<PhiFunction> physicalPhis) {
    super(new ArrayList<>(), new ArrayList<>());
    this.label = label;
    this.physicalPhis = physicalPhis;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
