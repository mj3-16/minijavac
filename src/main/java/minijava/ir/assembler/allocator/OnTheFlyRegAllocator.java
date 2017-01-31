package minijava.ir.assembler.allocator;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import minijava.ir.assembler.NodeAllocator;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.operands.ImmediateOperand;
import minijava.ir.assembler.operands.MemoryOperand;
import minijava.ir.assembler.operands.Operand;
import minijava.ir.assembler.operands.OperandWidth;
import minijava.ir.assembler.operands.Parameter;
import minijava.ir.assembler.operands.StackSlot;
import minijava.ir.assembler.registers.*;
import minijava.ir.utils.MethodInformation;
import org.jooq.lambda.tuple.Tuple2;

/**
 * Implementation of an on-the-fly register allocator. The resulting code uses the processor
 * registers much more than the {@link BasicRegAllocator}.
 */
public class OnTheFlyRegAllocator extends AbstractRegAllocator
    implements InstructionVisitor<List<Instruction>> {

  private Map<Operand, StackSlot> argumentStackSlots;
  private Map<StackSlot, Operand> usedStackSlots;
  /**
   * Empty optionals are equivalent to "there was a stack slot assigned here and was later removed,
   * but there are stack slots that have a bigger offset)
   */
  private List<Optional<StackSlot>> assignedStackSlots;

  private Map<AMD64Register, Operand> usedRegisters;
  private Map<Operand, AMD64Register> argumentsInRegisters;
  private Set<AMD64Register> freeRegisters;
  private int maxStackDepth;
  private int currentStackDepth;
  private Instruction currentInstruction;
  private List<AMD64Register> usableRegisters;
  /** Registers that contain value that should be written back if spilled. */
  private Set<AMD64Register> registersToSpill;

  public OnTheFlyRegAllocator(
      MethodInformation methodInfo, LinearCodeSegment code, NodeAllocator nodeAllocator) {
    super(code, nodeAllocator);
    this.usableRegisters = new ArrayList<>();
    this.usableRegisters.addAll(AMD64Register.usableRegisters);
    this.maxStackDepth = 0;
    this.currentStackDepth = 0;
    this.assignedStackSlots = new ArrayList<>();
    this.argumentStackSlots = new HashMap<>();
    this.usedRegisters = new HashMap<>();
    this.usedStackSlots = new HashMap<>();
    this.freeRegisters = new HashSet<>();
    this.argumentsInRegisters = new HashMap<>();
    this.registersToSpill = new HashSet<>();
    // setup the method parameters
    List<AMD64Register> argRegs = AMD64Register.methodArgumentQuadRegisters;
    for (int i = 0; i < Math.min(argRegs.size(), methodInfo.paramNumber); i++) {
      Parameter location = nodeAllocator.parameters.get(i);
      putArgumentIntoRegister(location, argRegs.get(i));
      registersToSpill.add(argRegs.get(i));
    }
    // put a pseudo element on the stack as we can't use the 0. stack slot (we backup the old frame pointer there)
    putArgumentOnStack(new ImmediateOperand(OperandWidth.Quad, 42));
    // add a stack slot for each stack passed parameter
    for (int i = argRegs.size(); i < methodInfo.paramNumber; i++) {
      Parameter parameter = nodeAllocator.parameters.get(i);
      putParameterOnStack(parameter);
    }
    this.freeRegisters =
        seq(usableRegisters).removeAll(usedRegisters.keySet()).collect(Collectors.toSet());
  }
  /*
   * Populates the stack with all NodeArguments used in this method that are used in more than one block.
   * This will result in a pretty large stack at the advantage of being simple
   */
  private List<Instruction> prepopulateStack() {
    List<Instruction> instructions = new ArrayList<>();
    // we first populate the stack with the parameters
    for (int i = 0;
        i
            < Math.min(
                AMD64Register.methodArgumentQuadRegisters.size(), nodeAllocator.parameters.size());
        i++) {
      Parameter param = nodeAllocator.parameters.get(i);
      putArgumentOnStack(param);
      AMD64Register paramReg =
          AMD64Register.methodArgumentQuadRegisters.get(i).ofWidth(param.width);
    }
    // we assign all NodeLocations that appear in instructions a stack slot
    for (LinearCodeSegment.InstructionOrString instructionOrString : code) {
      if (instructionOrString.instruction.isPresent()) {
        for (Operand arg : instructionOrString.instruction.get().getArguments()) {
          if (arg instanceof VirtualRegister
              && !(arg instanceof Parameter)
              && arg.instructionRelations.getNumberOfBlockUsages() != 1) {
            // we can omit all constants, registers that are only used in one block
            putArgumentOnStack(arg);
          }
        }
      }
    }
    return instructions;
  }

  /** Important: This method doesn't create any instructions (hence the return type void) */
  private void putArgumentIntoRegister(Operand operand, AMD64Register register) {
    register = register.ofWidth(operand.width);
    this.argumentsInRegisters.put(operand, register);
    this.usedRegisters.put(register, operand);
    if (freeRegisters.contains(register)) {
      freeRegisters.remove(register);
    }
    registersToSpill.remove(register);
  }

  private void removeArgumentFromRegister(Operand operand) {
    AMD64Register reg = argumentsInRegisters.get(operand);
    this.usedRegisters.remove(reg);
    this.argumentsInRegisters.remove(operand);
    this.freeRegisters.add(reg);
    registersToSpill.remove(reg);
  }

  /**
   * Removes all values from the stack that are only used in the current block and not from any
   * following instruction.
   */
  private void removeObsoleteArgumentsFromStack() {
    // we skip the first element that represents the backuped stack pointer that the don't want to remove
    List<Operand> removeArgs =
        assignedStackSlots
            .stream()
            .skip(1)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(this::isArgumentObsolete)
            .collect(Collectors.toList());
    removeArgs.forEach(this::removeArgumentFromStack);
  }

  /**
   * Is the passed operand not used in instructions that might follow the current instruction in an
   * execution?
   */
  private boolean isArgumentObsolete(Operand operand) {
    Operand.InstructionRelations relations = operand.instructionRelations;
    return (relations.getNumberOfBlockUsages() == 1
            && !relations.isUsedLaterInBlockOfInstruction(currentInstruction))
        || relations.isUsedAfterInstruction(currentInstruction);
  }

  private void putArgumentOnStack(Operand operand) {
    if (!hasStackSlotAssigned(operand)) {
      currentStackDepth += 8; //operand.width.sizeInBytes;
      StackSlot slot = new StackSlot(operand.width, -currentStackDepth);
      slot.setComment(operand.getComment());
      argumentStackSlots.put(operand, slot);
      usedStackSlots.put(slot, operand);
      assignedStackSlots.add(Optional.of(slot));
      if (currentStackDepth > maxStackDepth) {
        maxStackDepth = currentStackDepth;
      }
    }
  }

  private void putParameterOnStack(Parameter param) {
    if (!hasStackSlotAssigned(param)) {
      StackSlot slot = (StackSlot) getCurrentLocation(param);
      slot.setComment(param.getComment());
      argumentStackSlots.put(param, slot);
      usedStackSlots.put(slot, param);
      assignedStackSlots.add(Optional.of(slot));
    }
  }

  private void removeArgumentFromStack(Operand operand) {
    if (hasStackSlotAssigned(operand)) {
      final StackSlot slot = argumentStackSlots.get(operand);
      argumentStackSlots.remove(operand);
      assignedStackSlots.replaceAll(
          s -> s.isPresent() && s.get().equals(slot) ? Optional.empty() : s);
      usedStackSlots.remove(slot);
      resizeStack();
    }
  }

  private void resizeStack() {
    for (int i = assignedStackSlots.size() - 1; i >= 0; i--) {
      Optional<StackSlot> s = assignedStackSlots.get(i);
      if (s.isPresent()) {
        maxStackDepth = -s.get().offset + s.get().width.sizeInBytes;
        break;
      } else {
        assignedStackSlots.remove(i);
      }
    }
  }

  private AMD64Register chooseRegisterToEvict() {
    Operand evictedOperand = null;
    evictedOperand =
        seq(argumentsInRegisters.keySet())
            .filter(a -> !currentInstruction.getArguments().contains(a))
            .sorted(this::gradeArgumentToEvict)
            .reverse()
            .findAny()
            .get();
    assert evictedOperand != null;
    return argumentsInRegisters.get(evictedOperand);
  }

  /**
   * Assign's an operand (that should be located in a register) a score from -1 (don't evict it) and
   * 0 (evicting it is costly) to MAX_INT (evicting is cheap) It favors arguments that lie in
   * registers not used for method calling.
   */
  private int gradeArgumentToEvict(Operand operand) {
    if (currentInstruction.getArguments().contains(operand)) {
      // we shouldn't evict registers that contain arguments that the current instruction uses
      return -1;
    }
    int grade = 0;
    if (isArgumentObsolete(operand)) {
      // we can definitely use "spill" registers that are obsolete (not used in the following instructions)
      grade = 200;
    }
    if (!registersToSpill.contains(argumentsInRegisters.get(operand))) {
      // we can also cheaply use a register that has content that didn't change
      // (compared to it's original value from the stack…)
      grade = (grade + 1) * 2;
    }
    grade +=
        operand
            .instructionRelations
            .getNextUsageInBlock(currentInstruction)
            .map(Instruction::getNumberInSegment)
            .orElse(100);
    AMD64Register reg = argumentsInRegisters.get(operand);
    if (!AMD64Register.methodArgumentQuadRegisters.contains(reg)) {
      // we prefer register that aren't method arguments registers as this speeds up method calling
      grade += 50;
    }
    return grade;
  }

  private boolean needsToEvictRegister() {
    return freeRegisters.isEmpty();
  }

  public LinearCodeSegment process() {
    List<LinearCodeSegment.InstructionOrString> ret = new ArrayList<>();
    for (CodeBlock codeBlock : code.codeBlocks) {
      // process each code block
      for (LinearCodeSegment.InstructionOrString instructionOrString : codeBlock.getAllLines()) {
        if (instructionOrString.instruction.isPresent()) {
          currentInstruction = instructionOrString.instruction.get();
          if (currentInstruction instanceof MethodPrologue) {
            prepopulateStack()
                .stream()
                .map(LinearCodeSegment.InstructionOrString::new)
                .forEach(ret::add);
          }
          if (currentInstruction.getType().category == Instruction.Category.JMP) {
            visit(new Evict(seq(usedRegisters.keySet()).toList()))
                .forEach(x -> ret.add(new LinearCodeSegment.InstructionOrString(x)));
          }
          List<Instruction> replacement = currentInstruction.accept(this);
          replacement.stream().map(LinearCodeSegment.InstructionOrString::new).forEach(ret::add);
          //simpleRegisterIntegrityCheck();
          //System.err.println(currentInstruction);
          //System.err.println(getInformation());
          if (!currentInstruction.isMetaInstruction()) {
            // meta instructions can do weird stuff, therefore we don't remove any arguments from registers or stacks for them
            removeObsoleteArgumentsFromStack();
            //removeObsoleteArgumentsFromRegisters();
          }
        } else {
          ret.add(instructionOrString);
        }
      }
      visit(new Evict(seq(usedRegisters.keySet()).toList()))
          .forEach(x -> ret.add(new LinearCodeSegment.InstructionOrString(x)));
    }
    for (int i = 0; i < ret.size(); i++) {
      if (ret.get(i).instruction.isPresent()) {
        if (ret.get(i).instruction.get() instanceof MetaMethodFrameAlloc) {
          ret.set(i, new LinearCodeSegment.InstructionOrString(new AllocStack(maxStackDepth)));
        }
      }
    }
    return new LinearCodeSegment(code.codeBlocks, ret, code.getComments());
  }

  private boolean hasStackSlotAssigned(Operand operand) {
    return argumentStackSlots.containsKey(operand);
  }

  @Override
  public List<Instruction> visit(MethodPrologue prologue) {
    return ImmutableList.of(
        new Push(AMD64Register.BASE_POINTER).com("Backup old base pointer"),
        new Mov(AMD64Register.STACK_POINTER, AMD64Register.BASE_POINTER),
        new MetaMethodFrameAlloc());
  }

  @Override
  public List<Instruction> visit(Add add) {
    return visitBinaryInstruction(add, Add::new);
  }

  private List<Instruction> visitBinaryInstruction(
      BinaryInstruction binop, BiFunction<Operand, AMD64Register, Instruction> instrCreator) {
    return visitBinaryInstruction(binop, binop.left, binop.right, instrCreator, true);
  }

  /**
   * Like {@link OnTheFlyRegAllocator::visitBinaryInstruction} but ignores the previous value of the
   * right argument. This behaviour optimizes Mov and Load instructions as they overwrite the value
   * of the right argument anyway.
   */
  private List<Instruction> visitMovLikeBinaryInstruction(
      Instruction instruction,
      Operand left,
      Operand right,
      BiFunction<Operand, AMD64Register, Instruction> instrCreator) {
    return visitBinaryInstruction(instruction, left, right, instrCreator, false);
  }

  /**
   * Processes binary instructions
   *
   * @param instruction the instruction for tagging of the resulting instruction
   * @param left the left argument of the instruction
   * @param right the right argument of the instruction
   * @param instrCreator function that creates the actual instruction for two register arguments
   * @param loadRightValue load the value of the right argument in the result register, set to false
   *     for mov like instructions
   * @return
   */
  private List<Instruction> visitBinaryInstruction(
      Instruction instruction,
      Operand left,
      Operand right,
      BiFunction<Operand, AMD64Register, Instruction> instrCreator,
      boolean loadRightValue) {
    Tuple2<Optional<List<Instruction>>, AMD64Register> spillAndRegLeft = null;
    if (!(left instanceof ImmediateOperand
        && ((ImmediateOperand) left).fitsIntoImmPartOfInstruction())) {
      spillAndRegLeft = getRegisterForArgument(left);
    }
    Tuple2<Optional<List<Instruction>>, AMD64Register> spillAndRegRight =
        getRegisterForArgument(right, loadRightValue);
    List<Instruction> instructions = new ArrayList<>();
    Operand leftOperand = left;
    if (spillAndRegLeft != null) {
      spillAndRegLeft.v1.ifPresent(instructions::addAll);
      leftOperand = spillAndRegLeft.v2;
    }
    spillAndRegRight.v1.ifPresent(instructions::addAll);
    instructions.add(
        instrCreator.apply(leftOperand, spillAndRegRight.v2).firmAndComments(instruction));
    registersToSpill.add(spillAndRegRight.v2);
    return instructions;
  }

  private List<Instruction> visitUnaryInstruction(
      Instruction instruction, Operand arg, Function<AMD64Register, Instruction> instrCreator) {
    Tuple2<Optional<List<Instruction>>, AMD64Register> spillAndReg = getRegisterForArgument(arg);
    List<Instruction> instructions = new ArrayList<>();
    spillAndReg.v1.ifPresent(instructions::addAll);
    instructions.add(instrCreator.apply(spillAndReg.v2).firmAndComments(instruction));
    registersToSpill.add(spillAndReg.v2);
    return instructions;
  }

  @Override
  public List<Instruction> visit(AllocStack allocStack) {
    throw new RuntimeException();
  }

  @Override
  public List<Instruction> visit(And and) {
    return visitBinaryInstruction(and, And::new);
  }

  @Override
  public List<Instruction> visit(Call call) {
    throw new RuntimeException();
  }

  @Override
  public List<Instruction> visit(CLTD cltd) {
    return ImmutableList.of(cltd);
  }

  @Override
  public List<Instruction> visit(Cmp cmp) {
    return visitBinaryInstruction(cmp, Cmp::new);
  }

  @Override
  public List<Instruction> visit(ConditionalJmp jmp) {
    return ImmutableList.of(jmp);
  }

  @Override
  public List<Instruction> visit(DeallocStack deallocStack) {
    throw new RuntimeException();
  }

  @Override
  public List<Instruction> visit(Div div) {
    return ImmutableList.of(div);
  }

  @Override
  public List<Instruction> visit(Evict evict) {
    List<Instruction> instructions = new ArrayList<>();
    for (AMD64Register register : evict.registers) {
      evictFromRegister(register).ifPresent(instructions::add);
    }
    return instructions;
  }

  private Optional<Instruction> evictFromRegister(AMD64Register register) {
    return evictFromRegister(register, false);
  }

  @Override
  public List<Instruction> visit(Jmp jmp) {
    return ImmutableList.of(jmp);
  }

  /** Returns the current registers of the argument and doesn't generate any code. */
  private Operand getCurrentLocation(Operand arg) {
    if (arg instanceof VirtualRegister) {
      if (argumentsInRegisters.containsKey(arg)) {
        return argumentsInRegisters.get(arg);
      } else {
        if (arg instanceof Parameter) {
          if (((Parameter) arg).paramNumber >= AMD64Register.methodArgumentQuadRegisters.size()) {
            int inRegs = AMD64Register.methodArgumentQuadRegisters.size();
            int paramNum = ((Parameter) arg).paramNumber;
            return new StackSlot(arg.width, (paramNum - inRegs + 2) * 8);
          }
        }
        return argumentStackSlots.get(arg);
      }
    }
    // we don't have to care about "real" locations here
    return arg;
  }

  @Override
  public List<Instruction> visit(MetaCall metaCall) {
    List<AMD64Register> argRegs = AMD64Register.methodArgumentQuadRegisters;
    List<Instruction> instructions = new ArrayList<>();
    // we evict all argument passing registers
    AMD64Register.methodArgumentQuadRegisters
        .stream()
        .map(this::evictFromRegister)
        .forEach(o -> o.ifPresent(instructions::add));
    // move the register passed argument into the registers
    for (int i = 0; i < Math.min(argRegs.size(), metaCall.methodInfo.paramNumber); i++) {
      Operand arg = metaCall.args.get(i);
      if (getCurrentLocation(arg) != null) {
        instructions.add(
            new Mov(getCurrentLocation(arg), argRegs.get(i).ofWidth(arg.width))
                .com(String.format("Move %d.th param into register", i)));
      }
    }
    // we have than to evict all other registers
    seq(AMD64Register.usableRegisters)
        .removeAll(AMD64Register.methodArgumentQuadRegisters)
        .map(this::evictFromRegister)
        .forEach(o -> o.ifPresent(instructions::add));
    // the 64 ABI requires the stack to aligned to 16 bytes
    instructions.add(new Push(AMD64Register.STACK_POINTER).com("Save old stack pointer"));
    int stackAlignmentDecrement = 0;
    int stackDepthWithProlog = currentStackDepth + 8; // for saving %rbp
    if (stackDepthWithProlog % 16 != 0) { // the stack isn't aligned to 16 Bytes
      // we have to align the stack by decrementing the stack counter
      // this works because we can assume that the base pointer is correctly aligned
      // (induction and the main method is correctly called)
      stackAlignmentDecrement = 16 - stackDepthWithProlog % 16;
      instructions.add(
          new Add(
                  new ImmediateOperand(OperandWidth.Quad, -stackAlignmentDecrement),
                  AMD64Register.STACK_POINTER)
              .com("Decrement the stack to ensure alignment"));
    }
    // Use the tmpReg to move the additional parameters on the stack
    for (int i = metaCall.methodInfo.paramNumber - 1; i >= argRegs.size(); i--) {
      Operand arg = metaCall.args.get(i);
      instructions.add(new Push(getCurrentLocation(arg)));
    }
    instructions.add(
        new Call(
                metaCall.methodInfo.hasReturnValue
                    ? metaCall.result.get().width
                    : OperandWidth.Quad,
                metaCall.methodInfo.ldName)
            .com("Call the function")
            .firm(metaCall.firm()));
    instructions.add(
        new DeallocStack(
            Math.max(
                    0,
                    metaCall.methodInfo.paramNumber
                        - AMD64Register.methodArgumentQuadRegisters.size())
                * 8));
    if (stackAlignmentDecrement != 0) { // we aligned the stack explicitly before
      instructions.add(
          new Add(
                  new ImmediateOperand(OperandWidth.Quad, stackAlignmentDecrement),
                  AMD64Register.STACK_POINTER)
              .com("Remove stack alignment"));
    }
    instructions.add(
        new Mov(
                new MemoryOperand(OperandWidth.Quad, AMD64Register.STACK_POINTER, 0),
                AMD64Register.STACK_POINTER)
            .com("Restore old stack pointer"));
    if (metaCall.methodInfo.hasReturnValue) {
      Register ret = metaCall.result.get();
      Tuple2<Optional<List<Instruction>>, AMD64Register> registerForArgument =
          getRegisterForArgument(ret, false);
      registerForArgument.v1.ifPresent(instructions::addAll);
      putArgumentIntoRegister(ret, registerForArgument.v2);
      // now we move the return value from the %rax register into the return value's registers
      instructions.addAll(
          visit(new Mov(AMD64Register.RETURN_REGISTER.ofWidth(ret.width), registerForArgument.v2)));
      registersToSpill.add(registerForArgument.v2);
    }
    return instructions;
  }

  @Override
  public List<Instruction> visit(Mov mov) {
    return visitMovLikeBinaryInstruction(mov, mov.source, mov.destination, Mov::new);
  }

  @Override
  public List<Instruction> visit(MovFromSmallerToGreater mov) {
    //mov.com("bla");
    return visitMovLikeBinaryInstruction(
        mov,
        mov.source,
        mov.destination,
        (l, r) -> new MovFromSmallerToGreater(l, r.ofWidth(mov.source.width)));
  }

  @Override
  public List<Instruction> visit(Mul mul) {
    return visitBinaryInstruction(mul, Mul::new);
  }

  @Override
  public List<Instruction> visit(Neg neg) {
    return visitUnaryInstruction(neg, neg.arg, Neg::new);
  }

  @Override
  public List<Instruction> visit(Pop pop) {
    return visitUnaryInstruction(pop, pop.arg, Pop::new);
  }

  @Override
  public List<Instruction> visit(Push push) {
    return visitUnaryInstruction(push, push.arg, Push::new);
  }

  @Override
  public List<Instruction> visit(Ret ret) {
    return ImmutableList.of(ret);
  }

  @Override
  public List<Instruction> visit(final minijava.ir.assembler.instructions.Set set) {
    return visitUnaryInstruction(
        set, set.arg, arg -> new minijava.ir.assembler.instructions.Set(set.relation, arg));
  }

  @Override
  public List<Instruction> visit(Sub sub) {
    return visitBinaryInstruction(sub, (l, r) -> new Sub(l, r));
  }

  @Override
  public List<Instruction> visit(DisableRegisterUsage disableRegisterUsage) {
    usableRegisters.removeAll(disableRegisterUsage.registers);
    freeRegisters.removeAll(disableRegisterUsage.registers);
    return ImmutableList.of();
  }

  @Override
  public List<Instruction> visit(EnableRegisterUsage enableRegisterUsage) {
    for (AMD64Register register : enableRegisterUsage.registers) {
      if (!usableRegisters.contains(register)) {
        usableRegisters.add(register);
        freeRegisters.add(register);
      }
    }
    return ImmutableList.of();
  }

  private String getInformation() {
    StringBuilder builder = new StringBuilder();
    builder.append("Stack:\n").append(getStackInfo()).append("\n");
    builder.append("Registers:\n").append(getRegisterInfo());
    return builder.toString();
  }

  private String getStackInfo() {
    return assignedStackSlots
        .stream()
        .map(
            o -> {
              if (o.isPresent()) {
                return String.format(
                    "  %s -> %s", o.get().offset, usedStackSlots.get(o.get()).getComment());
              } else {
                return "  Empty";
              }
            })
        .collect(Collectors.joining("\n"));
  }

  private String getRegisterInfo() {
    return argumentsInRegisters
        .keySet()
        .stream()
        .map(a -> String.format("  %s -> %s", argumentsInRegisters.get(a), a.toString()))
        .collect(Collectors.joining("\n"));
  }

  /**
   * Checks that all registers in the free set aren't currently in use and that all usable registers
   * are either used or free.
   *
   * @throws RuntimeException if this isn't the case
   */
  private void simpleRegisterIntegrityCheck() {
    String prefix = "";
    if (currentInstruction != null) {
      prefix = currentInstruction.toGNUAssembler() + ": ";
    }
    List<AMD64Register> incorrectRegisters =
        seq(freeRegisters).retainAll(usedRegisters.keySet()).collect(Collectors.toList());
    if (incorrectRegisters.size() > 0) {
      throw new RuntimeException(
          prefix
              + "Registers "
              + incorrectRegisters.stream().map(Object::toString).collect(Collectors.joining(", "))
              + " are free and used at the same time");
    }
    incorrectRegisters =
        seq(usableRegisters)
            .removeAll(freeRegisters)
            .removeAll(usedRegisters.keySet())
            .collect(Collectors.toList());
    if (incorrectRegisters.size() > 0) {
      throw new RuntimeException(
          prefix
              + "Registers "
              + incorrectRegisters.stream().map(Object::toString).collect(Collectors.joining(", "))
              + " are neither free nor used");
    }
    incorrectRegisters = seq(this.freeRegisters).filter(x -> x == null).toList();
    if (incorrectRegisters.size() > 0) {
      throw new RuntimeException(
          prefix + "null in free registers: freeregisters = " + freeRegisters);
    }
  }

  private Optional<Instruction> evictFromRegister(AMD64Register register, boolean forceSpill) {
    if (usedRegisters.containsKey(register)) {
      Operand arg = usedRegisters.get(register);
      Optional<Instruction> spill =
          genSpillRegisterInstruction(register.ofWidth(arg.width), forceSpill);
      removeArgumentFromRegister(arg);
      freeRegisters.add(register);
      return spill;
    }
    return Optional.empty();
  }

  private Optional<Instruction> genSpillRegisterInstruction(AMD64Register register) {
    return genSpillRegisterInstruction(register, false);
  }

  /**
   * It generates an instruction that spills the value stored in the passed register if the value
   * has to be backuped. A value can be dismissed if …
   *
   * <ul>
   *   <li>it's a constant value
   *   <li>it's value didn't change
   * </ul>
   *
   * It doesn't remove the register or the value from any data structure, but creates a stack
   * registers if needed.
   */
  private Optional<Instruction> genSpillRegisterInstruction(
      AMD64Register register, boolean forceSpilling) {
    Operand operand = usedRegisters.get(register);
    if (!forceSpilling
        && (operand instanceof ImmediateOperand
            || !registersToSpill.contains(register)
            || !isArgumentObsolete(operand))) {
      // we don't have to do anything (see method comment)
      return Optional.empty();
    }
    // we have to spill the value
    if (!hasStackSlotAssigned(operand)) {
      // if we don't have already assigned a stack slot, we create one
      // we use the method because it handles the data structures...
      putArgumentOnStack(operand);
    }
    StackSlot spillSlot = argumentStackSlots.get(operand);
    Instruction spillInstruction =
        new Mov(register.ofWidth(operand.width), spillSlot)
            .com(String.format("Evict %s to %s (%s)", register, spillSlot, operand));
    return Optional.of(spillInstruction);
  }

  /**
   * Returns the register that contains the arguments value if the returned instruction were
   * executed before
   *
   * <p>Attention the operand shouldn't be a constant. Use constants directly as an instruction
   * operand.
   *
   * @return (optional spill and reload instructions, register the operand is placed in)
   */
  private Tuple2<Optional<List<Instruction>>, AMD64Register> getRegisterForArgument(
      Operand operand) {
    return getRegisterForArgument(operand, true);
  }

  /**
   * Returns the register that is assigned to the passed operand
   *
   * <p>Attention the operand shouldn't be a constant. Use constants directly as an instruction
   * operand
   *
   * @param restoreValue restore the old value of the operand in the register if possible
   * @return (optional spill and reload instructions, register the operand is placed in)
   */
  private Tuple2<Optional<List<Instruction>>, AMD64Register> getRegisterForArgument(
      Operand operand, boolean restoreValue) {
    if (operand instanceof AMD64Register) {
      // a valid register was already passed as an operand
      return new Tuple2<Optional<List<Instruction>>, AMD64Register>(
          Optional.empty(), (AMD64Register) operand);
    }
    if (argumentsInRegisters.containsKey(operand)) {
      // the operand is already in a register, nothing to do...
      return new Tuple2<Optional<List<Instruction>>, AMD64Register>(
          Optional.empty(), argumentsInRegisters.get(operand));
    }
    OperandWidth width = operand.width;
    List<Instruction> instructions = new ArrayList<>();
    AMD64Register register;
    if (needsToEvictRegister()) {
      // we need to evict registers here
      register = chooseRegisterToEvict().ofWidth(width);
      genSpillRegisterInstruction(register.ofWidth(width)).ifPresent(instructions::add);
      Operand priorOperand = usedRegisters.get(register);
      removeArgumentFromRegister(priorOperand);
    } else {
      // we have enough registers
      Optional<AMD64Register> nonMethodArgRegister =
          seq(freeRegisters).removeAll(AMD64Register.methodArgumentQuadRegisters).findFirst();
      register =
          nonMethodArgRegister
              .orElseGet(() -> seq(freeRegisters.iterator()).findFirst().get())
              .ofWidth(width);
      freeRegisters.remove(register);
    }
    putArgumentIntoRegister(operand, register);
    Operand currentPlace = null;
    if (hasStackSlotAssigned(operand)) {
      // the operand was evicted once, we have to load its value
      currentPlace = argumentStackSlots.get(operand);
    } else if (operand instanceof ImmediateOperand) {
      // this is usually a sign that something went wrong, as this case should be been captured early an the constant
      // placed directly into the instruction
      // the only instance where this seems to happen is for Cmp instructions
      currentPlace = operand;
    }
    boolean invalidConst =
        operand instanceof ImmediateOperand
            && !((ImmediateOperand) operand).fitsIntoImmPartOfInstruction();
    if (currentPlace != null && (restoreValue || invalidConst)) {
      if (invalidConst) {
        // we handle constants here that are too big for the imm part of an instruction
        long value = ((ImmediateOperand) operand).value;
        assert value > Integer.MAX_VALUE;
        instructions.add(
            new Mov(new ImmediateOperand(operand.width, 0), register)
                .com(
                    String.format("%s doesn't fit into the imm part of an instruction...", value)));
        while (value > Integer.MAX_VALUE) {
          value -= Integer.MAX_VALUE;
          instructions.add(
              new Add(new ImmediateOperand(operand.width, Integer.MAX_VALUE), register));
        }
        instructions.add(new Add(new ImmediateOperand(operand.width, value), register));
      } else {
        instructions.add(
            new Mov(currentPlace, register).com(String.format("Move %s into register", operand)));
      }
    }
    return new Tuple2<Optional<List<Instruction>>, AMD64Register>(
        Optional.of(instructions), register);
  }
}
