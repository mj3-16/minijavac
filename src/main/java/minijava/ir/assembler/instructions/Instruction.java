package minijava.ir.assembler.instructions;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;

public abstract class Instruction {
  public final List<Operand> operands;
  public final List<Register> defined;

  protected Instruction(List<Operand> operands, List<Register> defined) {
    Preconditions.checkArgument(!operands.contains(null), "null Operand");
    Preconditions.checkArgument(!defined.contains(null), "null result register");
    this.operands = operands;
    this.defined = defined;
  }

  protected Instruction(Operand operand, Register result) {
    this(Lists.newArrayList(operand), Lists.newArrayList(result));
  }

  protected Instruction(Operand left, Operand right, Register result) {
    this(Lists.newArrayList(left, right), Lists.newArrayList(result));
  }

  protected Instruction(Operand left, Operand right) {
    this(Lists.newArrayList(left, right), new ArrayList<>());
  }

  protected Instruction(Operand operand, Register resultLow, Register resultHigh) {
    this(Lists.newArrayList(operand), Lists.newArrayList(resultLow, resultHigh));
  }

  protected Instruction(Operand left, Operand right, Register resultLow, Register resultHigh) {
    this(Lists.newArrayList(left, right), Lists.newArrayList(resultLow, resultHigh));
  }

  protected static boolean isConstrainedToRegister(Register reg, AMD64Register constraint) {
    if (reg instanceof VirtualRegister) {
      return ((VirtualRegister) reg).constraint == constraint;
    }
    return reg == constraint;
  }

  interface Visitor {}
}
