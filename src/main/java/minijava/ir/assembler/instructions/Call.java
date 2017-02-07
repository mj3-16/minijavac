package minijava.ir.assembler.instructions;

import static org.jooq.lambda.Seq.seq;

import java.util.List;
import minijava.ir.assembler.VirtualRegisterMapping;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.VirtualRegister;

public class Call extends Instruction {
  public String label;

  public Call(String label, List<Operand> arguments, VirtualRegisterMapping mapping) {
    super(arguments, temporariesForAllocatableRegisters(mapping));
    this.label = label;
  }

  private static List<VirtualRegister> temporariesForAllocatableRegisters(
      VirtualRegisterMapping mapping) {
    return seq(AMD64Register.allocatable).map(reg -> temporaryConstraintTo(mapping, reg)).toList();
  }

  private static VirtualRegister temporaryConstraintTo(
      VirtualRegisterMapping mapping, AMD64Register register) {
    VirtualRegister tmp = mapping.freshTemporary();
    tmp.constraint = register;
    return tmp;
  }
}
