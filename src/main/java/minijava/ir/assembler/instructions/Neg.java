package minijava.ir.assembler.instructions;

import static com.google.common.collect.Lists.newArrayList;

import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.RegisterOperand;
import minijava.ir.assembler.registers.VirtualRegister;

public class Neg extends Instruction {
  public final Operand input;
  public final Operand output;

  public Neg(RegisterOperand operand, VirtualRegister result) {
    this(operand, new RegisterOperand(operand.width, result));
  }

  public Neg(RegisterOperand input, RegisterOperand output) {
    super(newArrayList(input), newArrayList(output));
    this.input = input;
    this.output = output;
  }

  public Neg(MemoryOperand inout) {
    super(newArrayList(inout), newArrayList(inout));
    this.input = inout;
    this.output = inout;
  }

  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}
