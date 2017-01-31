package minijava.ir.assembler.instructions;

import minijava.ir.assembler.location.Register;
import minijava.ir.utils.AssemblerUtils;

/** Constant argument for assembler instructions */
public class ConstOperand extends Operand {

  public final long value;

  public ConstOperand(Register.Width width, long value) {
    super(width);
    this.value = value;
  }

  @Override
  public String toGNUAssembler() {
    return "$" + value;
  }

  @Override
  public String toString() {
    return String.format("Const(%d)", value);
  }

  public boolean fitsIntoImmPartOfInstruction() {
    return AssemblerUtils.doesIntegerFitIntoImmPartOfInstruction(value);
  }
}
