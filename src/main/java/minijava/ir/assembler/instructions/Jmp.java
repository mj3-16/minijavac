package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

public class Jmp extends Instruction {
  private final String label;

  public Jmp(String label) {
    super(newArrayList(), newArrayList());
    this.label = label;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
