package minijava.ir.assembler.instructions;

import static org.jooq.lambda.Seq.seq;

import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;

public abstract class Instruction {
  public final List<Operand> inputs;
  public final List<VirtualRegister> outputs;

  protected Instruction(List<Operand> inputs, List<VirtualRegister> outputs) {
    Preconditions.checkArgument(!inputs.contains(null), "null input operand");
    Preconditions.checkArgument(!outputs.contains(null), "null output register");
    this.inputs = inputs;
    this.outputs = outputs;
  }

  public Set<VirtualRegister> usages() {
    Set<VirtualRegister> usages = new HashSet<>();
    for (Operand input : inputs) {
      input.match(
          imm -> {},
          reg -> addIfVirtualRegister(reg.register, usages),
          mem -> addIfVirtualRegister(mem.mode.index, addIfVirtualRegister(mem.mode.base, usages)));
    }
    return usages;
  }

  private Set<VirtualRegister> addIfVirtualRegister(
      Register register, Set<VirtualRegister> registers) {
    if (register instanceof VirtualRegister) {
      registers.add((VirtualRegister) register);
    }
    return registers;
  }

  protected static boolean isConstrainedToRegister(Register reg, AMD64Register constraint) {
    if (reg instanceof VirtualRegister) {
      return ((VirtualRegister) reg).constraint == constraint;
    }
    return reg == constraint;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName());
    sb.append('(');
    sb.append(String.join(", ", seq(inputs).map(Object::toString)));
    sb.append(')');
    if (outputs.size() > 0) {
      sb.append(" -> ");
      sb.append(String.join(", ", seq(outputs).map(Object::toString)));
    }
    return sb.toString();
  }

  interface Visitor {}
}
