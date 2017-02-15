package minijava.backend.instructions;

import firm.Relation;
import java.util.ArrayList;

public class Jcc extends Instruction {
  public final String label;
  public final Relation relation;

  public Jcc(String label, Relation relation) {
    super(new ArrayList<>(), new ArrayList<>());
    this.label = label;
    this.relation = relation;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
