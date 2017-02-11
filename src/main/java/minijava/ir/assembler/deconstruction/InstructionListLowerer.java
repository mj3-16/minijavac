package minijava.ir.assembler.deconstruction;

import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.RELOAD;
import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.SPILL;
import static org.jooq.lambda.Seq.seq;

import java.util.ArrayList;
import java.util.List;
import minijava.ir.assembler.StackLayout;
import minijava.ir.assembler.allocation.AllocationResult;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.operands.*;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jooq.lambda.function.Function2;
import org.jooq.lambda.function.Function3;

public class InstructionListLowerer implements Instruction.Visitor {
  private final CodeBlock block;
  private final AllocationResult allocationResult;
  private final List<Instruction> highLevel;
  private final ArrayList<Instruction> lowered = new ArrayList<>();
  private int instructionCounter;

  private InstructionListLowerer(
      CodeBlock block, AllocationResult allocationResult, List<Instruction> highLevel) {
    this.block = block;
    this.allocationResult = allocationResult;
    this.highLevel = highLevel;
  }

  public List<Instruction> lower() {
    for (int i = 0; i < highLevel.size(); ++i) {
      instructionCounter = i;
      BlockPosition def = new BlockPosition(block, BlockPosition.definedBy(i));
      BlockPosition use = new BlockPosition(block, BlockPosition.usedBy(i));
      AllocationResult.SpillEvent beforeUse = allocationResult.spillEvents.get(use);
      if (beforeUse != null) {
        addReload(beforeUse);
      }
      highLevel.get(i).accept(this);
      AllocationResult.SpillEvent afterDef = allocationResult.spillEvents.get(def);
      if (afterDef != null) {
        addSpill(afterDef);
      }
    }
    return lowered;
  }

  private void addSpill(AllocationResult.SpillEvent afterDef) {
    assert afterDef.kind == SPILL;
    AMD64Register assigned = allocationResult.allocation.get(afterDef.interval);
    assert assigned != null;
    VirtualRegister virtual = afterDef.interval.register;
    OperandWidth slotWidth = virtual.defWidth;
    RegisterOperand src = new RegisterOperand(slotWidth, assigned);
    MemoryOperand dest = allocationResult.spillLocation(slotWidth, virtual);
    lowered.add(new Mov(src, dest));
  }

  private void addReload(AllocationResult.SpillEvent beforeUse) {
    assert beforeUse.kind == RELOAD;
    AMD64Register assigned = allocationResult.allocation.get(beforeUse.interval);
    assert assigned != null;
    VirtualRegister virtual = beforeUse.interval.register;
    OperandWidth slotWidth = virtual.defWidth;
    MemoryOperand src = allocationResult.spillLocation(slotWidth, virtual);
    RegisterOperand dest = new RegisterOperand(slotWidth, assigned);
    lowered.add(new Mov(src, dest));
  }

  @Override
  public void visit(Add add) {
    lowered.add(substitutedTwoAddressInstruction(add, Add::new, Add::new));
  }

  @Override
  public void visit(And and) {
    lowered.add(substitutedTwoAddressInstruction(and, And::new, And::new));
  }

  @Override
  public void visit(IMul imul) {
    lowered.add(substitutedTwoAddressInstruction(imul, IMul::new, IMul::new));
  }

  @Override
  public void visit(Sub sub) {
    lowered.add(substitutedTwoAddressInstruction(sub, Sub::new, Sub::new));
  }

  private TwoAddressInstruction substitutedTwoAddressInstruction(
      TwoAddressInstruction instruction,
      Function3<Operand, RegisterOperand, RegisterOperand, TwoAddressInstruction> regFactory,
      Function2<Operand, MemoryOperand, TwoAddressInstruction> memFactory) {
    Operand left = substituteHardwareRegisters(instruction.left, false);
    Operand rightIn = substituteHardwareRegisters(instruction.rightIn, false);
    Operand rightOut = substituteHardwareRegisters(instruction.rightOut, true);
    // After allocation both rightIn and rightOut MUST be the same Operands, otherwise we can't use a 2-address
    // instruction.
    assert rightIn.equals(rightOut);

    return rightIn.match(
        imm -> {
          throw new UnsupportedOperationException("Can't output into an immediate operand");
        },
        reg -> {
          return regFactory.apply(left, reg, reg);
        },
        mem -> {
          return memFactory.apply(left, mem);
        });
  }

  @Override
  public void visit(Call call) {
    lowered.add(call);
  }

  @Override
  public void visit(Cltd cltd) {
    lowered.add(cltd);
  }

  @Override
  public void visit(Cmp cmp) {
    lowered.add(
        new Cmp(
            substituteHardwareRegisters(cmp.left, false),
            substituteHardwareRegisters(cmp.right, false)));
  }

  @Override
  public void visit(Enter enter) {
    RegisterOperand rsp = wholeRegister(AMD64Register.SP);
    RegisterOperand rbp = wholeRegister(AMD64Register.BP);
    lowered.add(new Push(rbp));
    lowered.add(new Mov(rsp, rbp));
    int maxNumberOfSpills = seq(allocationResult.spillSlots.values()).max().orElse(0);
    int activationRecordSize = StackLayout.BYTES_PER_STACK_SLOT * maxNumberOfSpills;
    ImmediateOperand minuend = new ImmediateOperand(OperandWidth.Quad, activationRecordSize);
    lowered.add(new Sub(minuend, rsp, rsp));
  }

  private static RegisterOperand wholeRegister(Register register) {
    return new RegisterOperand(OperandWidth.Quad, register);
  }

  @Override
  public void visit(IDiv idiv) {
    lowered.add(new IDiv(substituteHardwareRegisters(idiv.divisor, false)));
  }

  @Override
  public void visit(Jcc jcc) {
    // Jumps shouldn't yet be there at this point. It's pretty clear how to handle them, though.
    lowered.add(jcc);
  }

  @Override
  public void visit(Jmp jmp) {
    // Jumps shouldn't yet be there at this point. It's pretty clear how to handle them, though.
    lowered.add(jmp);
  }

  @Override
  public void visit(Label label) {
    // Labels shouldn't yet be there at this point. It's pretty clear how to handle them, though.
    lowered.add(label);
  }

  @Override
  public void visit(Leave leave) {
    RegisterOperand rsp = wholeRegister(AMD64Register.SP);
    RegisterOperand rbp = wholeRegister(AMD64Register.BP);
    lowered.add(new Mov(rbp, rsp));
    lowered.add(new Pop(rbp));
  }

  @Override
  public void visit(Mov mov) {
    Operand src = substituteHardwareRegisters(mov.src, false);
    Operand dest = substituteHardwareRegisters(mov.dest, true);
    Mov substituted =
        dest.match(
            imm -> {
              throw new UnsupportedOperationException("Can't Mov into an immediate");
            },
            reg -> {
              return new Mov(src, reg);
            },
            mem -> {
              return new Mov(src, mem);
            });
    lowered.add(substituted);
  }

  @Override
  public void visit(Neg neg) {
    Operand input = substituteHardwareRegisters(neg.input, false);
    Operand output = substituteHardwareRegisters(neg.input, true);
    // After allocation, the input should be the same operand as the output (as we can't translate it to AMD64 code
    // otherwise).
    assert input.equals(output);
    Neg substituted =
        output.match(
            imm -> {
              throw new UnsupportedOperationException("Can't Neg an immediate");
            },
            reg -> {
              return new Neg(reg, reg);
            },
            mem -> {
              return new Neg(mem);
            });
    lowered.add(substituted);
  }

  @Override
  public void visit(Pop pop) {
    Operand output = substituteHardwareRegisters(pop.output, true);
    Pop substituted =
        output.match(
            imm -> {
              throw new UnsupportedOperationException("Can't Pop into an immediate");
            },
            reg -> {
              return new Pop(reg);
            },
            mem -> {
              return new Pop(mem);
            });
    lowered.add(substituted);
  }

  @Override
  public void visit(Push push) {
    lowered.add(new Push(substituteHardwareRegisters(push.input, false)));
  }

  @Override
  public void visit(Ret ret) {
    lowered.add(ret);
  }

  @Override
  public void visit(Setcc setcc) {
    Operand output = substituteHardwareRegisters(setcc.output, true);
    Setcc substituted =
        output.match(
            imm -> {
              throw new UnsupportedOperationException("Can't Setcc an immediate");
            },
            reg -> {
              return new Setcc(reg, setcc.relation);
            },
            mem -> {
              return new Setcc(mem, setcc.relation);
            });
    lowered.add(substituted);
  }

  @Override
  public void visit(Test test) {
    Operand left = substituteHardwareRegisters(test.left, false);
    Operand right = substituteHardwareRegisters(test.left, false);
    Test substituted =
        left.match(
            imm -> {
              throw new UnsupportedOperationException("Can't Test an immediate left");
            },
            reg -> {
              return new Test(reg, right);
            },
            mem -> {
              return new Test(mem, right);
            });
    lowered.add(substituted);
  }

  private Operand substituteHardwareRegisters(Operand virtualOperand, boolean isOutput) {
    BlockPosition def = new BlockPosition(block, BlockPosition.definedBy(instructionCounter));
    BlockPosition use = new BlockPosition(block, BlockPosition.usedBy(instructionCounter));
    return virtualOperand.match(
        imm -> imm,
        reg -> {
          AMD64Register register = assignedRegisterAt(reg.register, isOutput ? def : use);
          if (register == null) {
            // Return a memory operand to the spill slot
            MemoryOperand spillSlot =
                allocationResult.spillLocation(reg.width, (VirtualRegister) reg.register);
            //
            return spillSlot;
          }
          return new RegisterOperand(reg.width, register);
        },
        mem -> {
          AMD64Register base = assignedRegisterAt(mem.mode.base, use);
          AMD64Register index = assignedRegisterAt(mem.mode.index, use);
          // We can't handle MemoryOperands where the referenced registers are spilled (yet).
          // That would entail rewriting the MemoryOperand as a series of Push/Pops.
          // Fortunately, this only happens when register pressure is really high and we make use of elaborate
          // Addressing modes in instruction selection.
          assert mem.mode.base == null || base != null;
          assert mem.mode.index == null || index != null;
          return new MemoryOperand(
              mem.width, new AddressingMode(mem.mode.displacement, base, index, mem.mode.scale));
        });
  }

  private AMD64Register assignedRegisterAt(Register register, BlockPosition position) {
    if (register instanceof AMD64Register) {
      return (AMD64Register) register;
    }
    return allocationResult.assignedRegisterAt((VirtualRegister) register, position);
  }

  public static List<Instruction> lower(
      CodeBlock block, AllocationResult allocationResult, List<Instruction> highLevelInstructions) {
    return new InstructionListLowerer(block, allocationResult, highLevelInstructions).lower();
  }
}
