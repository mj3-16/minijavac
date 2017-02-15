package minijava.backend.instructions;

import static com.google.common.base.Preconditions.checkArgument;
import static minijava.ir.utils.FirmUtils.modeToWidth;

import com.google.common.collect.Lists;
import firm.Mode;
import firm.Relation;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.Operand;

public class Setcc extends CodeBlockInstruction {
  public final Operand output;
  public final Relation relation;

  public Setcc(Operand op, Relation relation) {
    // Implicit input: the flags register
    super(Lists.newArrayList(), Lists.newArrayList(op));
    assert op.width == modeToWidth(Mode.getb());
    checkArgument(op.width == modeToWidth(Mode.getb()), "Operand to set must have width of mode b");
    checkArgument(!(op instanceof ImmediateOperand), "Can't set an immediate");
    this.relation = relation;
    this.output = op;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
