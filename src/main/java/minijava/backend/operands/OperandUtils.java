package minijava.backend.operands;

import minijava.backend.registers.Register;

/** Some short forms for creating anonymous Operands, defaulting to a width of OperandWidth.Quad. */
public class OperandUtils {
  public static RegisterOperand reg(Register register) {
    return new RegisterOperand(OperandWidth.Quad, register);
  }

  public static ImmediateOperand imm(long value) {
    return new ImmediateOperand(OperandWidth.Quad, value);
  }

  public static MemoryOperand mem(AddressingMode mode) {
    return new MemoryOperand(OperandWidth.Quad, mode);
  }
}
