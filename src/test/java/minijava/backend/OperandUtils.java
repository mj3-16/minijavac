package minijava.backend;

import minijava.backend.operands.AddressingMode;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.Register;

/** Some short forms for creating Operands, defaulting to a width of OperandWidth.Quad. */
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
