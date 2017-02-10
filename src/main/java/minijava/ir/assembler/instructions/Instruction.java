package minijava.ir.assembler.instructions;

import static org.jooq.lambda.Seq.seq;

import com.google.common.base.Preconditions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.Register;

public abstract class Instruction {
  // We need to separate input from output operands in order to differentiate usages from defs.
  // Mostly input operands subsume output operands but there are cases such as Mov, where
  // the RHS is not an input if it's a RegisterOperand.
  // We need to store output operands as operands rather than registers, because otherwise we would
  // not have the information needed for lowering.
  private final List<Operand> inputs;
  private final List<Operand> outputs;
  private final Set<Operand> hints = new HashSet<>();

  protected Instruction(List<Operand> inputs, List<Operand> outputs) {
    Preconditions.checkArgument(!inputs.contains(null), "null input operand");
    Preconditions.checkArgument(!outputs.contains(null), "null output register");
    this.inputs = inputs;
    this.outputs = outputs;
  }

  protected void setHints(Operand... shouldBeAssignedTheSameRegister) {
    for (Operand operand : shouldBeAssignedTheSameRegister) {
      assert inputs.contains(operand) || outputs.contains(operand)
          : "Can only hint connections between input and output operands";
      hints.add(operand);
    }
  }

  public Set<Register> usages() {
    Set<Register> usages = new HashSet<>();
    for (Operand input : inputs) {
      input.match(
          imm -> {},
          reg -> usages.add(reg.register),
          mem -> {
            usages.add(mem.mode.index);
            usages.add(mem.mode.base);
          });
    }
    for (MemoryOperand mem : seq(outputs).ofType(MemoryOperand.class)) {
      // These are special in that the registers mentioned in the address mode are also usages.
      usages.add(mem.mode.index);
      usages.add(mem.mode.base);
    }
    return usages;
  }

  public Set<Register> definitions() {
    return seq(outputs).ofType(RegisterOperand.class).map(def -> def.register).toSet();
  }

  protected static List<Operand> toOperands(Iterable<? extends Register> registers) {
    return toOperands(OperandWidth.Quad, registers);
  }

  protected static List<Operand> toOperands(
      OperandWidth width, Iterable<? extends Register> registers) {
    return seq(registers).map(reg -> (Operand) new RegisterOperand(width, reg)).toList();
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

  public Set<Register> registerHints() {
    Set<Register> hints = new HashSet<>();
    for (RegisterOperand operand : seq(hints).ofType(RegisterOperand.class)) {
      hints.add(operand.register);
    }
    return hints;
  }
}
