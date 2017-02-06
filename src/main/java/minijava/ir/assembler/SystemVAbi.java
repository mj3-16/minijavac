package minijava.ir.assembler;

import minijava.ir.assembler.operands.AddressingMode;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.utils.MethodInformation;

public class SystemVAbi {
  public static final AMD64Register[] ARG_REGISTERS = {
    AMD64Register.DI,
    AMD64Register.SI,
    AMD64Register.D,
    AMD64Register.C,
    AMD64Register.R8,
    AMD64Register.R9
  };

  public static final AMD64Register RETURN_REGISTER = AMD64Register.A;

  /**
   * This is also dictated by the System V ABI. For temporary spills (which aren't specified by
   * System V), we *could* use some other layouting mechanism.
   */
  public static final int BYTES_PER_ACTIVATION_RECORD_SLOT = 8;
  /** The saved BP and the return address are stored between (%bp) and the first argument. */
  private static final int SLOTS_BETWEEN_BP_AND_ARGS = 2;

  /**
   * The first six (integer/pointer) parameters are passed through ARG_REGISTERS, the others on the
   * stack.
   */
  public static Operand argument(int index, OperandWidth width) {
    if (index < ARG_REGISTERS.length) {
      return new RegisterOperand(width, ARG_REGISTERS[index]);
    }
    index -= ARG_REGISTERS.length;
    index += SLOTS_BETWEEN_BP_AND_ARGS; // Saved BP + return address
    int offset = index * BYTES_PER_ACTIVATION_RECORD_SLOT;
    AddressingMode address = AddressingMode.offsetFromRegister(AMD64Register.BP, offset);
    return new MemoryOperand(width, address);
  }

  /** Analogous to {@link #argument}, but addresses stack slots relative from the callees SP. */
  public static Operand parameter(int index, OperandWidth width) {
    if (index < ARG_REGISTERS.length) {
      return new RegisterOperand(width, ARG_REGISTERS[index]);
    }
    index -= ARG_REGISTERS.length;
    int offset = index * BYTES_PER_ACTIVATION_RECORD_SLOT;
    AddressingMode address = AddressingMode.offsetFromRegister(AMD64Register.SP, offset);
    return new MemoryOperand(width, address);
  }

  public static int parameterRegionSize(MethodInformation info) {
    return (info.paramNumber - ARG_REGISTERS.length) * 8;
  }
}
