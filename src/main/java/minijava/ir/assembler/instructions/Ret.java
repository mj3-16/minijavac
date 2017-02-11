package minijava.ir.assembler.instructions;

import java.util.ArrayList;

public class Ret extends Instruction {
  public Ret() {
    super(new ArrayList<>(), new ArrayList<>());
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
