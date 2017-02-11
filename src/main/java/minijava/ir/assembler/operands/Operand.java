package minijava.ir.assembler.operands;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import minijava.ir.assembler.registers.Register;

/** Operand for an assembler instruction */
public abstract class Operand {
  public final OperandWidth width;

  public Operand(OperandWidth width) {
    this.width = width;
  }

  public Operand withChangedWidth(OperandWidth width) {
    if (this.width == width) {
      return this;
    }
    return withChangedWidthImpl(width);
  }

  abstract Operand withChangedWidthImpl(OperandWidth width);

  public abstract <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem);

  public void match(
      Consumer<ImmediateOperand> matchImm,
      Consumer<RegisterOperand> matchReg,
      Consumer<MemoryOperand> matchMem) {
    match(
        imm -> {
          matchImm.accept(imm);
          return null;
        },
        reg -> {
          matchReg.accept(reg);
          return null;
        },
        mem -> {
          matchMem.accept(mem);
          return null;
        });
  }

  public Set<Register> reads(boolean inOutputPosition) {
    return match(
        imm -> {
          return Sets.newHashSet();
        },
        reg -> inOutputPosition ? Sets.newHashSet() : Sets.newHashSet(reg.register),
        mem -> Sets.newHashSet(mem.mode.index, mem.mode.base));
  }
}
