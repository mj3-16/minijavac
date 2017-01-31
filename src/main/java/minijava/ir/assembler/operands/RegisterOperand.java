package minijava.ir.assembler.operands;

import minijava.ir.assembler.registers.Register;

public class RegisterOperand extends Operand {

  private final Register register;

  public RegisterOperand(OperandWidth width, Register register) {
    super(width);
    this.register = register;
  }

  @Override
  public String toGNUAssembler() {
    return null;
  }
}
