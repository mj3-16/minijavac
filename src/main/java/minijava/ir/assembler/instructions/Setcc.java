package minijava.ir.assembler.instructions;

import static minijava.ir.utils.FirmUtils.modeToWidth;

import com.google.common.collect.Lists;
import firm.Mode;
import firm.Relation;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;

public class Setcc extends Instruction {
  public final Operand output;
  public final Relation relation;

  public Setcc(RegisterOperand op, Relation relation) {
    // Implicit input: the flags register
    super(Lists.newArrayList(), Lists.newArrayList(op));
    assert op.width == modeToWidth(Mode.getb());
    this.relation = relation;
    this.output = op;
  }

  public Setcc(MemoryOperand op, Relation relation) {
    // Implicit input: the flags register
    super(Lists.newArrayList(), Lists.newArrayList(op));
    assert op.width == modeToWidth(Mode.getb());
    this.relation = relation;
    this.output = op;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
