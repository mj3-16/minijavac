package minijava.backend.syntax;

import static minijava.ir.utils.FirmUtils.relationToInstructionSuffix;
import static org.jooq.lambda.Seq.seq;

import firm.Graph;
import firm.nodes.Node;
import java.util.List;
import java.util.Map.Entry;
import minijava.backend.allocation.AllocationResult;
import minijava.backend.block.PhiFunction;
import minijava.backend.instructions.Add;
import minijava.backend.instructions.And;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Cmp;
import minijava.backend.instructions.Cqto;
import minijava.backend.instructions.Enter;
import minijava.backend.instructions.IDiv;
import minijava.backend.instructions.IMul;
import minijava.backend.instructions.Instruction;
import minijava.backend.instructions.Jcc;
import minijava.backend.instructions.Jmp;
import minijava.backend.instructions.Label;
import minijava.backend.instructions.Leave;
import minijava.backend.instructions.Mov;
import minijava.backend.instructions.Neg;
import minijava.backend.instructions.Pop;
import minijava.backend.instructions.Push;
import minijava.backend.instructions.Ret;
import minijava.backend.instructions.Setcc;
import minijava.backend.instructions.Sub;
import minijava.backend.instructions.Test;
import minijava.backend.instructions.TwoAddressInstruction;
import minijava.backend.instructions.Xchg;
import minijava.backend.operands.AddressingMode;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import minijava.ir.emit.NameMangler;

public class GasSyntax implements Instruction.Visitor {
  private final StringBuilder builder;
  private final Graph graph;
  private final AllocationResult allocationResult;

  private GasSyntax(StringBuilder builder, Graph graph, AllocationResult allocationResult) {
    this.builder = builder;
    this.graph = graph;
    this.allocationResult = allocationResult;
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
    if (pop.output.irNode != null) {
      appendCommentLine(pop.output.irNode.toString(), true);
    }
    formatInstruction(pop, pop.output.width, pop.output);
  }

  @Override
  public void visit(Push push) {
    if (push.input.irNode != null) {
      appendCommentLine(push.input.irNode.toString(), true);
    }
    formatInstruction(push, push.input.width, push.input);
  }

  @Override
  public void visit(Ret ret) {
    formatInstruction(ret);
  }

  @Override
  public void visit(Xchg xchg) {
    if (xchg.left.irNode != null && xchg.right.irNode != null) {
      appendCommentLine(xchg.left.irNode + " <-> " + xchg.right.irNode, true);
    }
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
  public void visit(Cqto cqto) {
    appendLine("cqto", true);
  }

  @Override
  public void visit(Cmp cmp) {
    if (cmp.left.irNode != null && cmp.right.irNode != null) {
      appendCommentLine("Compare " + cmp.left.irNode + " to " + cmp.right.irNode, true);
    }
    formatInstruction(cmp, cmp.left.width, cmp.left, cmp.right);
  }

  @Override
  public void visit(Enter enter) {
    throw new UnsupportedOperationException(
        "Can't assemble Enter. Should have been lowered before.");
  }

  @Override
  public void visit(IDiv idiv) {
    if (idiv.divisor.irNode != null) {
      appendCommentLine("Divides by " + idiv.divisor.irNode, true);
    }
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
    if (mov.src instanceof ImmediateOperand) {
      appendCommentLine("Define " + mov.src.irNode, true);
    } else if (mov.src.irNode != null && mov.src.irNode.equals(mov.dest.irNode)) {
      if (mov.src instanceof MemoryOperand) {
        AddressingMode mode = ((MemoryOperand) mov.src).mode;
        if (mode.base == AMD64Register.BP) {
          appendCommentLine("Reload/Resolve " + mov.src.irNode, true);
        } else {
          appendCommentLine("Load " + mov.src.irNode, true);
        }
      } else if (mov.dest instanceof MemoryOperand) {
        appendCommentLine("Spill/Resolve " + mov.dest.irNode, true);
      } else {
        // Considering all handled cases this must be reg to reg
        assert mov.src instanceof RegisterOperand && mov.dest instanceof RegisterOperand;
        appendCommentLine("Resolving move of " + mov.dest.irNode, true);
      }
    } else if (mov.src.irNode != null || mov.dest.irNode != null) {
      appendCommentLine(String.format("Copy %s to %s", mov.src.irNode, mov.dest.irNode), true);
    }
    formatInstruction(mov, mov.dest.width, mov.src, mov.dest);
  }

  @Override
  public void visit(Neg neg) {
    if (neg.inout.irNode != null) {
      appendCommentLine("Define " + neg.inout.irNode, true);
    }
    formatInstruction(neg, neg.inout.width, neg.inout);
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
    if (test.left.irNode != null && test.left.irNode.equals(test.right.irNode)) {
      appendCommentLine("Rematerialize " + test.left.irNode, true);
    }
    formatInstruction(test, test.left.width, test.left, test.right);
  }

  private void formatTwoAddressInstruction(TwoAddressInstruction tai) {
    appendCommentLine(
        String.format("Defines %s (lhs: %s)", tai.right.irNode, tai.left.irNode), true);
    formatInstruction(tai, tai.left.width, tai.left, tai.right);
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
    boolean first = true;
    for (Operand operand : output) {
      if (!first) {
        builder.append(',');
      }
      first = false;
      builder.append(' ');
      formatOperand(operand);
    }
    appendLine();
  }

  private void formatOperand(Operand output) {
    output.match(
        imm -> {
          builder.append('$');
          builder.append(imm.value);
        },
        reg -> {
          formatRegister(reg.width, (AMD64Register) reg.register);
        },
        mem -> {
          formatAddressMode(mem.mode);
        });
  }

  private void formatAddressMode(AddressingMode mode) {
    if (mode.displacement != 0) {
      builder.append(mode.displacement);
    }
    boolean hasBase = mode.base != null;
    boolean hasIndex = mode.index != null;
    if (hasBase || hasIndex) {
      builder.append('(');
      if (hasBase) {
        formatRegister(OperandWidth.Quad, (AMD64Register) mode.base);
      }
      if (hasIndex) {
        builder.append(',');
        formatRegister(OperandWidth.Quad, (AMD64Register) mode.index);
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
    builder.append('%');
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

  private void formatGraphInstructions(List<Instruction> instructions) {
    appendCommentLine("Stack frame layout:", false);
    seq(allocationResult.spillSlots.entrySet())
        .groupBy(Entry::getValue)
        .forEach(
            (slot, registers) -> {
              List<Node> values = seq(registers).map(e -> e.getKey().value).toList();
              appendCommentLine(String.format("%5d: %s", slot, values), false);
            });

    instructions.forEach(i -> i.accept(this));
    appendLine();
  }

  private void appendCommentLine(String comment, boolean indent) {
    if (indent) {
      builder.append("    ");
    }
    builder.append("// ");
    appendLine(comment, false);
  }

  public static void formatGraphInstructions(
      StringBuilder builder,
      List<Instruction> instructions,
      Graph graph,
      AllocationResult allocationResult) {
    new GasSyntax(builder, graph, allocationResult).formatGraphInstructions(instructions);
  }

  public static void formatHeader(StringBuilder builder) {
    builder.append(".p2align 4, 0x90, 15");
    builder.append(System.lineSeparator());
    builder.append(".globl ");
    builder.append(NameMangler.mangledMainMethodName());
    builder.append(System.lineSeparator());
    builder.append(System.lineSeparator());
  }
}
