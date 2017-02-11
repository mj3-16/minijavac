package minijava.ir.assembler.syntax;

import static minijava.ir.utils.FirmUtils.relationToInstructionSuffix;

import java.util.List;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.operands.AddressingMode;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.emit.NameMangler;

public class GasSyntax implements Instruction.Visitor {
  private final List<Instruction> instructions;
  private final StringBuilder builder = new StringBuilder();

  private GasSyntax(List<Instruction> instructions) {
    this.instructions = instructions;
  }

  @Override
  public void visit(Jcc jcc) {
    String cc = relationToInstructionSuffix(jcc.relation);
    indent();
    builder.append("j");
    builder.append(cc);
    builder.append(" ");
    builder.append(jcc.label);
    appendLine();
  }

  @Override
  public void visit(Jmp jmp) {
    indent();
    builder.append("jmp ");
    builder.append(jmp.label);
    appendLine();
  }

  @Override
  public void visit(Label label) {
    builder.append(label.label);
    builder.append(":");
    appendLine();
  }

  @Override
  public void visit(PhiFunction phi) {
    throw new UnsupportedOperationException(
        "Can't assemble PhiFunction. Should not have been mentioned in the instruction list to begin with.");
  }

  @Override
  public void visit(Pop pop) {
    formatInstruction(pop, pop.output.width, pop.output);
  }

  @Override
  public void visit(Push push) {
    formatInstruction(push, push.input.width, push.input);
  }

  @Override
  public void visit(Ret ret) {
    formatInstruction(ret);
  }

  @Override
  public void visit(Xchg xchg) {
    formatInstruction(xchg, xchg.left.width, xchg.left, xchg.right);
  }

  @Override
  public void visit(Add add) {
    formatTwoAddressInstruction(add);
  }

  @Override
  public void visit(And and) {
    formatTwoAddressInstruction(and);
  }

  @Override
  public void visit(Call call) {
    appendLine("call " + call.label, true);
  }

  @Override
  public void visit(Cltd cltd) {
    appendLine("cltd", true);
  }

  @Override
  public void visit(Cmp cmp) {
    formatInstruction(cmp, cmp.left.width, cmp.left, cmp.right);
  }

  @Override
  public void visit(Enter enter) {
    throw new UnsupportedOperationException(
        "Can't assemble Enter. Should have been lowered before.");
  }

  @Override
  public void visit(IDiv idiv) {
    formatInstruction(idiv, idiv.divisor.width, idiv.divisor);
  }

  @Override
  public void visit(IMul imul) {
    formatTwoAddressInstruction(imul);
  }

  @Override
  public void visit(Leave leave) {
    throw new UnsupportedOperationException(
        "Can't assemble Leave. Should have been lowered before.");
  }

  @Override
  public void visit(Mov mov) {
    formatInstruction(mov, mov.dest.width, mov.src, mov.dest);
  }

  @Override
  public void visit(Neg neg) {
    assert neg.input.equals(neg.output);
    formatInstruction(neg, neg.input.width, neg.input);
  }

  @Override
  public void visit(Setcc setcc) {
    indent();
    builder.append("set");
    builder.append(relationToInstructionSuffix(setcc.relation));
    builder.append(" ");
    formatOperand(setcc.output);
    appendLine();
  }

  @Override
  public void visit(Sub sub) {
    formatTwoAddressInstruction(sub);
  }

  @Override
  public void visit(Test test) {
    formatInstruction(test, test.left.width, test.left, test.right);
  }

  private void formatTwoAddressInstruction(TwoAddressInstruction tai) {
    assert tai.rightIn.equals(tai.rightOut);
    formatInstruction(tai, tai.left.width, tai.left, tai.rightIn);
  }

  private void formatInstruction(Instruction instruction) {
    indent();
    builder.append(toMnemonic(instruction));
    appendLine();
  }

  private String toMnemonic(Instruction instruction) {
    return instruction.getClass().getSimpleName().toLowerCase();
  }

  private void formatInstruction(Instruction instruction, OperandWidth width, Operand... output) {
    indent();
    builder.append(toMnemonic(instruction));
    builder.append(widthToInstructionSuffix(width));
    for (Operand operand : output) {
      builder.append(" ");
      formatOperand(operand);
    }
    appendLine();
  }

  private void formatOperand(Operand output) {
    output.match(
        imm -> {
          builder.append('$');
          builder.append(Long.toHexString(imm.value));
        },
        reg -> {
          formatRegister(reg.width, (AMD64Register) reg.register);
        },
        mem -> {
          formatAddressMode(mem.mode);
        });
  }

  private void formatAddressMode(AddressingMode mode) {
    StringBuilder builder = new StringBuilder();
    if (mode.displacement != 0) {
      builder.append(Integer.toHexString(mode.displacement));
    }
    boolean hasBase = mode.base != null;
    boolean hasIndex = mode.index != null;
    if (hasBase || hasIndex) {
      builder.append('(');
      if (hasBase) {
        builder.append(mode.base);
      }
      if (hasIndex) {
        builder.append(',');
        builder.append(mode.index);
        builder.append(',');
        builder.append(mode.scale);
      }
      builder.append(')');
    }
  }

  private void formatRegister(OperandWidth width, AMD64Register register) {
    int widthIdx = width.ordinal();
    String[][] prefix = {{"", "e", "r"}, {"", "e", "r"}, {"", "", ""}};
    String[][] suffix = {{"l", "x", "x"}, {"l", "", ""}, {"b", "d", ""}};
    switch (register) {
      case A:
      case B:
      case C:
      case D:
        builder.append(prefix[0][widthIdx]);
        builder.append(register.toString().toLowerCase());
        builder.append(suffix[0][widthIdx]);
        break;
      case SP:
      case BP:
      case SI:
      case DI:
        builder.append(prefix[1][widthIdx]);
        builder.append(register.toString().toLowerCase());
        builder.append(suffix[1][widthIdx]);
        break;
      case R8:
      case R9:
      case R10:
      case R11:
      case R12:
      case R13:
      case R14:
      case R15:
        builder.append(prefix[2][widthIdx]);
        builder.append(register.toString().toLowerCase());
        builder.append(suffix[2][widthIdx]);
        break;
    }
  }

  private static String widthToInstructionSuffix(OperandWidth width) {
    switch (width) {
      case Byte:
        return "b";
      case Long:
        return "l";
      case Quad:
        return "q";
    }
    return "";
  }

  private StringBuilder formatAssembler() {
    appendLine(".p2align 4, 0x90, 15", true);
    appendLine(".globl " + NameMangler.mangledMainMethodName(), true);
    appendLine();
    instructions.forEach(i -> i.accept(this));
    return builder;
  }

  private void appendLine() {
    builder.append(System.lineSeparator());
  }

  private void appendLine(String line, boolean indent) {
    if (indent) {
      indent();
    }
    builder.append(line);
    appendLine();
  }

  private void indent() {
    builder.append("    ");
  }

  public static StringBuilder formatAssembler(List<Instruction> instructions) {
    return new GasSyntax(instructions).formatAssembler();
  }
}
