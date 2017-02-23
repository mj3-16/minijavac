package minijava.backend.deconstruction;

import static minijava.backend.allocation.AllocationResult.SpillEvent.Kind.RELOAD;
import static minijava.backend.allocation.AllocationResult.SpillEvent.Kind.SPILL;
import static minijava.ir.utils.FirmUtils.modeToWidth;
import static org.jooq.lambda.Seq.seq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import minijava.backend.SystemVAbi;
import minijava.backend.allocation.AllocationResult;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.PhiFunction;
import minijava.backend.instructions.Add;
import minijava.backend.instructions.And;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Cmp;
import minijava.backend.instructions.CodeBlockInstruction;
import minijava.backend.instructions.Cqto;
import minijava.backend.instructions.Enter;
import minijava.backend.instructions.IDiv;
import minijava.backend.instructions.IMul;
import minijava.backend.instructions.Instruction;
import minijava.backend.instructions.Label;
import minijava.backend.instructions.Leave;
import minijava.backend.instructions.Mov;
import minijava.backend.instructions.Neg;
import minijava.backend.instructions.Pop;
import minijava.backend.instructions.Push;
import minijava.backend.instructions.Setcc;
import minijava.backend.instructions.Sub;
import minijava.backend.instructions.Test;
import minijava.backend.instructions.TwoAddressInstruction;
import minijava.backend.lifetime.BlockPosition;
import minijava.backend.operands.AddressingMode;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.MemoryOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.AMD64Register;
import minijava.backend.registers.Register;
import minijava.backend.registers.VirtualRegister;
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
    BlockPosition phiDef = BlockPosition.definedBy(block, -1);
    for (AllocationResult.SpillEvent afterPhiDef : allocationResult.spillEvents.get(phiDef)) {
      System.out.println("phiDef = " + phiDef);
      System.out.println("afterPhiDef = " + afterPhiDef);
      addSpill(afterPhiDef);
    }
    for (int i = 0; i < highLevel.size(); ++i) {
      instructionCounter = i;
      BlockPosition def = BlockPosition.definedBy(block, i);
      BlockPosition use = BlockPosition.usedBy(block, i);
      for (AllocationResult.SpillEvent beforeUse : allocationResult.spillEvents.get(use)) {
        System.out.println("use = " + use);
        System.out.println("beforeUse = " + beforeUse);
        addReload(beforeUse);
      }
      highLevel.get(i).accept(this);
      for (AllocationResult.SpillEvent afterDef : allocationResult.spillEvents.get(def)) {
        System.out.println("def = " + def);
        System.out.println("afterDef = " + afterDef);
        addSpill(afterDef);
      }
    }
    // TODO: reload before phi?
    return lowered;
  }

  private void addSpill(AllocationResult.SpillEvent afterDef) {
    assert afterDef.kind == SPILL;
    AMD64Register assigned = allocationResult.allocation.get(afterDef.interval);
    if (assigned == null) {
      // Nothing to do, the value is already stored at its spill location
      return;
    }
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
  public void visit(Cqto cqto) {
    lowered.add(cqto);
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
    int maxNumberOfSpills =
        seq(allocationResult.spillSlots.values()).map(i -> i + 1).max().orElse(0);
    // We will always use an even number of spill slots, so that we don't have to take the activation record size
    // into account when realizing the System V ABI.
    if (maxNumberOfSpills % 2 == 1) {
      maxNumberOfSpills++;
    }
    return SystemVAbi.BYTES_PER_ACTIVATION_RECORD_SLOT * maxNumberOfSpills;
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
          System.out.println("mem = " + mem);
          BlockPosition use = position;
          if (position.isDef()) {
            // Register access to memory operands counts as a use before the actual def.
            // this is relevant for mov sth, (%reg), where reg is a use, not a def.
            use = new BlockPosition(position.block, position.pos - 1);
          }
          AMD64Register base = allocationResult.assignedRegisterAt(mem.mode.base, use);
          AMD64Register index = allocationResult.assignedRegisterAt(mem.mode.index, use);
          System.out.println("base = " + base);
          System.out.println("index = " + index);
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
