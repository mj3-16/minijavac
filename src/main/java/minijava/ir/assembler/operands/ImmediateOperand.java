package minijava.ir.assembler.operands;

import minijava.ir.utils.AssemblerUtils;

/** Constant operand for assembler instructions */
public class ImmediateOperand extends Operand {

  public final long value;

  public ImmediateOperand(OperandWidth width, long value) {
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
