package minijava.ir.assembler.deconstruction;

import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.RELOAD;
import static minijava.ir.assembler.allocation.AllocationResult.SpillEvent.Kind.SPILL;
import static minijava.ir.utils.FirmUtils.modeToWidth;
import static org.jooq.lambda.Seq.seq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.ir.assembler.StackLayout;
import minijava.ir.assembler.allocation.AllocationResult;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.PhiFunction;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.lifetime.BlockPosition;
import minijava.ir.assembler.operands.*;
import minijava.ir.assembler.registers.AMD64Register;
import minijava.ir.assembler.registers.Register;
import minijava.ir.assembler.registers.VirtualRegister;
import org.jooq.lambda.function.Function2;

public class InstructionListLowerer implements CodeBlockInstruction.Visitor {
  private final CodeBlock block;
  private final AllocationResult allocationResult;
  private final ArrayList<Instruction> lowered = new ArrayList<>();
  private int instructionCounter;

  private InstructionListLowerer(CodeBlock block, AllocationResult allocationResult) {
    this.block = block;
    this.allocationResult = allocationResult;
  }

  public List<Instruction> lowerBlock() {
    instructionCounter = -1;
    Label label = new Label(block.label, seq(block.phis).map(this::substitutePhi).toList());
    lowered.add(label);

    List<CodeBlockInstruction> highLevel = block.instructions;
    for (int i = 0; i < highLevel.size(); ++i) {
      instructionCounter = i;
      BlockPosition def = BlockPosition.definedBy(block, i);
      BlockPosition use = BlockPosition.usedBy(block, i);
      AllocationResult.SpillEvent beforeUse = allocationResult.spillEvents.get(use);
      if (beforeUse != null) {
        System.out.println("use = " + use);
        System.out.println("beforeUse = " + beforeUse);
        addReload(beforeUse);
      }
      highLevel.get(i).accept(this);
      AllocationResult.SpillEvent afterDef = allocationResult.spillEvents.get(def);
      if (afterDef != null) {
        System.out.println("def = " + def);
        System.out.println("afterDef = " + afterDef);
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
    OperandWidth slotWidth = modeToWidth(virtual.value.getMode());
    RegisterOperand src = new RegisterOperand(slotWidth, assigned);
    MemoryOperand dest = allocationResult.spillLocation(slotWidth, virtual);
    lowered.add(new Mov(src, dest));
  }

  private void addReload(AllocationResult.SpillEvent beforeUse) {
    System.out.println("beforeUse = " + beforeUse.interval.register);
    assert beforeUse.kind == RELOAD;
    AMD64Register assigned = allocationResult.allocation.get(beforeUse.interval);
    assert assigned != null;
    VirtualRegister virtual = beforeUse.interval.register;
    OperandWidth slotWidth = modeToWidth(virtual.value.getMode());
    MemoryOperand src = allocationResult.spillLocation(slotWidth, virtual);
    RegisterOperand dest = new RegisterOperand(slotWidth, assigned);
    lowered.add(new Mov(src, dest));
  }

  public PhiFunction substitutePhi(PhiFunction phi) {
    Map<CodeBlock, Operand> inputs = new HashMap<>();
    phi.inputs.forEach(
        (block, input) ->
            inputs.put(block, substituteHardwareRegisters(input, BlockPosition.endOf(block))));
    Operand output = substituteHardwareRegisters(phi.output, BlockPosition.beginOf(this.block));
    return new PhiFunction(inputs, output, phi.phi);
  }

  @Override
  public void visit(Add add) {
    lowered.add(substitutedBinaryInstruction(add, Add::new));
  }

  @Override
  public void visit(And and) {
    lowered.add(substitutedBinaryInstruction(and, And::new));
  }

  @Override
  public void visit(IMul imul) {
    lowered.add(substitutedBinaryInstruction(imul, IMul::new));
  }

  @Override
  public void visit(Sub sub) {
    lowered.add(substitutedBinaryInstruction(sub, Sub::new));
  }

  private TwoAddressInstruction substitutedBinaryInstruction(
      TwoAddressInstruction instruction,
      Function2<Operand, Operand, TwoAddressInstruction> factory) {
    Operand left = substituteHardwareRegisters(instruction.left, currentUse());
    Operand right = substituteHardwareRegisters(instruction.right, currentUse());
    return factory.apply(left, right);
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
            substituteHardwareRegisters(cmp.left, currentUse()),
            substituteHardwareRegisters(cmp.right, currentUse())));
  }

  @Override
  public void visit(Enter enter) {
    RegisterOperand rsp = wholeRegister(AMD64Register.SP);
    RegisterOperand rbp = wholeRegister(AMD64Register.BP);
    lowered.add(new Push(rbp));
    lowered.add(new Mov(rsp, rbp));
    int activationRecordSize = activationRecordSize(allocationResult);
    ImmediateOperand minuend = new ImmediateOperand(OperandWidth.Quad, activationRecordSize);
    lowered.add(new Sub(minuend, rsp));
  }

  private static int activationRecordSize(AllocationResult allocationResult) {
    int maxNumberOfSpills = seq(allocationResult.spillSlots.values()).max().orElse(0) + 1;
    // We will always use an even number of spill slots, so that we don't have to take the activation record size
    // into account when realizing the System V ABI.
    if (maxNumberOfSpills % 2 == 1) {
      maxNumberOfSpills++;
    }
    return StackLayout.BYTES_PER_STACK_SLOT * maxNumberOfSpills;
  }

  private static RegisterOperand wholeRegister(Register register) {
    return new RegisterOperand(OperandWidth.Quad, register);
  }

  @Override
  public void visit(IDiv idiv) {
    lowered.add(new IDiv(substituteHardwareRegisters(idiv.divisor, currentUse())));
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
    Operand src = substituteHardwareRegisters(mov.src, currentUse());
    Operand dest = substituteHardwareRegisters(mov.dest, currentDef());
    lowered.add(new Mov(src, dest));
  }

  @Override
  public void visit(Neg neg) {
    Operand inout = substituteHardwareRegisters(neg.inout, currentUse());
    lowered.add(new Neg(inout));
  }

  @Override
  public void visit(Setcc setcc) {
    Operand output = substituteHardwareRegisters(setcc.output, currentDef());
    lowered.add(new Setcc(output, setcc.relation));
  }

  @Override
  public void visit(Test test) {
    Operand left = substituteHardwareRegisters(test.left, currentUse());
    Operand right = substituteHardwareRegisters(test.right, currentUse());
    lowered.add(new Test(left, right));
  }

  private BlockPosition currentUse() {
    return BlockPosition.usedBy(block, instructionCounter);
  }

  private BlockPosition currentDef() {
    return BlockPosition.definedBy(block, instructionCounter);
  }

  private Operand substituteHardwareRegisters(Operand virtualOperand, BlockPosition position) {
    return virtualOperand.match(
        imm -> imm,
        reg -> {
          return allocationResult.hardwareOperandAt(reg.width, reg.register, position);
        },
        mem -> {
          AMD64Register base = allocationResult.assignedRegisterAt(mem.mode.base, position);
          AMD64Register index = allocationResult.assignedRegisterAt(mem.mode.index, position);
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

  public static List<Instruction> lowerBlock(CodeBlock block, AllocationResult allocationResult) {
    return new InstructionListLowerer(block, allocationResult).lowerBlock();
  }
}
