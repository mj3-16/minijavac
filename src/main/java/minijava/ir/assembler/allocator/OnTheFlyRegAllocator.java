package minijava.ir.assembler.allocator;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import minijava.ir.assembler.SimpleNodeAllocator;
import minijava.ir.assembler.block.CodeBlock;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.location.*;
import minijava.ir.utils.MethodInformation;
import org.jooq.lambda.tuple.Tuple2;

/**
 * This is a basic implementation of a register allocator, that allocates {@link NodeLocation}
 * instances in registers and the heap.
 *
 * <p>It's pretty slow (stores everything on the stack) but works well.
 */
public class OnTheFlyRegAllocator extends AbstractRegAllocator
    implements InstructionVisitor<List<Instruction>> {

  private Map<Argument, StackSlot> argumentStackSlots;
  private Map<StackSlot, Argument> usedStackSlots;
  /**
   * Empty optionals are equivalent to "there was a stack slot assigned here and was later removed,
   * but there are stack slots that have a bigger offset)
   */
  private List<Optional<StackSlot>> assignedStackSlots;

  private Map<Register, Argument> usedRegisters;
  private Map<Argument, Register> argumentsInRegisters;
  private Set<Register> freeRegisters;
  private int maxStackDepth;
  private int currentStackDepth;
  private Instruction currentInstruction;
  private List<Register> usableRegisters;

  public OnTheFlyRegAllocator(
      MethodInformation methodInfo, LinearCodeSegment code, SimpleNodeAllocator nodeAllocator) {
    super(methodInfo, code, nodeAllocator);
    this.usableRegisters = new ArrayList<>();
    this.usableRegisters.addAll(Register.usableRegisters);
    this.maxStackDepth = 0;
    this.currentStackDepth = 0;
    this.assignedStackSlots = new ArrayList<>();
    this.argumentStackSlots = new HashMap<>();
    this.usedRegisters = new HashMap<>();
    this.usedStackSlots = new HashMap<>();
    this.freeRegisters = new HashSet<>();
    this.argumentsInRegisters = new HashMap<>();
    // setup the method parameters
    List<Register> argRegs = Register.methodArgumentQuadRegisters;
    for (int i = 0; i < Math.min(argRegs.size(), methodInfo.paramNumber); i++) {
      ParamLocation location = nodeAllocator.paramLocations.get(i);
      if (location.isUsed()) {
        // we can omit unused parameters
        putArgumentIntoRegister(location, argRegs.get(i));
      }
    }
    // put a pseudo element on the stack as we can't use the 0. stack slot (we backup the old frame pointer there)
    putArgumentOnStack(new ConstArgument(Register.Width.Quad, 42));
    // add a stack slot for each stack passed parameter
    for (int i = argRegs.size(); i < methodInfo.paramNumber; i++) {
      ParamLocation paramLocation = nodeAllocator.paramLocations.get(i);
      putParameterOnStack(paramLocation);
    }
    this.freeRegisters =
        seq(usableRegisters).removeAll(usedRegisters.keySet()).collect(Collectors.toSet());
  }

  private List<Instruction> prepopulateStack() {
    List<Instruction> instructions = new ArrayList<>();
    // we populate the stack with all NodeArguments used in this method
    // this will result in a pretty large stack at the advantage of being simple
    // we first populate the stack with the parameters
    for (int i = 0;
        i
            < Math.min(
                Register.methodArgumentQuadRegisters.size(), nodeAllocator.paramLocations.size());
        i++) {
      ParamLocation param = nodeAllocator.paramLocations.get(i);
      putArgumentOnStack(param);
      Register paramReg = Register.methodArgumentQuadRegisters.get(i).ofWidth(param.width);
    }
    // we assign all NodeLocations that appear in instructions a stack slot
    for (LinearCodeSegment.InstructionOrString instructionOrString : code) {
      if (instructionOrString.instruction.isPresent()) {
        for (Argument arg : instructionOrString.instruction.get().getArguments()) {
          if (arg instanceof NodeLocation && !(arg instanceof ParamLocation)) {
            putArgumentOnStack(arg);
          }
        }
      }
    }
    return instructions;
  }

  /** Important: This method doesn't create any instructions (hence the return type void) */
  private void putArgumentIntoRegister(Argument argument, Register register) {
    register = register.ofWidth(argument.width);
    this.argumentsInRegisters.put(argument, register);
    this.usedRegisters.put(register, argument);
    if (freeRegisters.contains(register)) {
      freeRegisters.remove(register);
    }
  }

  private void removeArgumentFromRegister(Argument argument) {
    Register reg = argumentsInRegisters.get(argument);
    this.usedRegisters.remove(reg);
    this.argumentsInRegisters.remove(argument);
    this.freeRegisters.add(reg);
  }

  private void removeNeverAgainUsedArgumentsFromRegisters() {
    for (Argument argument : ImmutableSet.copyOf(usedRegisters.values())) {
      if (!isArgumentUsedInOtherBlock(argument)) {
        removeArgumentFromRegister(argument);
      }
    }
  }

  private void removeNeverAgainUsedArgumentsFromStack() {
    // we skip the first element that represents the backuped stack pointer that the don't want to remove
    List<Argument> removeArgs =
        assignedStackSlots
            .stream()
            .skip(1)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(a -> !isArgumentUsedInOtherBlock(a))
            .collect(Collectors.toList());
    removeArgs.forEach(this::removeArgumentFromStack);
  }

  private boolean isArgumentUsedInOtherBlock(Argument argument) {
    return argument.instructionRelations.getNumberOfBlockUsages() > 1;
  }

  private void putArgumentOnStack(Argument argument) {
    if (!hasStackSlotAssigned(argument)) {
      currentStackDepth += 8; //argument.width.sizeInBytes;
      StackSlot slot = new StackSlot(argument.width, -currentStackDepth);
      slot.setComment(argument.getComment());
      argumentStackSlots.put(argument, slot);
      usedStackSlots.put(slot, argument);
      assignedStackSlots.add(Optional.of(slot));
      if (currentStackDepth > maxStackDepth) {
        maxStackDepth = currentStackDepth;
      }
    }
  }

  private void putParameterOnStack(ParamLocation param) {
    if (!hasStackSlotAssigned(param)) {
      StackSlot slot = (StackSlot) getCurrentLocation(param);
      slot.setComment(param.getComment());
      argumentStackSlots.put(param, slot);
      usedStackSlots.put(slot, param);
      assignedStackSlots.add(Optional.of(slot));
    }
  }

  private void removeArgumentFromStack(Argument argument) {
    if (hasStackSlotAssigned(argument)) {
      final StackSlot slot = argumentStackSlots.get(argument);
      argumentStackSlots.remove(argument);
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

  private Register chooseRegisterToEvict() {
    Argument evictedArgument = null;
    for (Argument argument : argumentsInRegisters.keySet()) {
      if (!currentInstruction.getArguments().contains(argument)) {
        evictedArgument = argument;
      }
    }
    assert evictedArgument != null;
    return argumentsInRegisters.get(evictedArgument);
  }

  private Register chooseRegister() {
    if (argumentsInRegisters.isEmpty()) {
      return usableRegisters.get(0);
    }
    return chooseRegisterToEvict();
  }

  private boolean needsToEvictRegister() {
    return freeRegisters.isEmpty();
  }

  public LinearCodeSegment process() {
    List<LinearCodeSegment.InstructionOrString> ret = new ArrayList<>();
    for (CodeBlock codeBlock : code.codeBlocks) {
      // process each code block
      boolean evictedInBlock = false;
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
            if (!evictedInBlock) {
              visit(new Evict(Register.usableRegisters))
                  .forEach(x -> ret.add(new LinearCodeSegment.InstructionOrString(x)));
            }
            evictedInBlock = true;
          }
          List<Instruction> replacement = currentInstruction.accept(this);
          replacement.stream().map(LinearCodeSegment.InstructionOrString::new).forEach(ret::add);
          //simpleRegisterIntegrityCheck();
          //System.err.println(currentInstruction);
          //System.err.println(getInformation());
          if (!currentInstruction.isMetaInstruction()) {
            //removeNeverAgainUsedArgumentsFromStack();
            //removeNeverAgainUsedArgumentsFromRegisters();
          }
        } else {
          ret.add(instructionOrString);
        }
      }
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

  private boolean hasStackSlotAssigned(Argument argument) {
    return argumentStackSlots.containsKey(argument);
  }

  @Override
  public List<Instruction> visit(MethodPrologue prologue) {
    return ImmutableList.of(
        new Push(Register.BASE_POINTER).com("Backup old base pointer"),
        new Mov(Register.STACK_POINTER, Register.BASE_POINTER),
        new MetaMethodFrameAlloc());
  }

  @Override
  public List<Instruction> visit(Add add) {
    return visitBinaryInstruction(add, Add::new);
  }

  private List<Instruction> visitBinaryInstruction(
      BinaryInstruction binop, BiFunction<Argument, Register, Instruction> instrCreator) {
    return visitBinaryInstruction(binop, binop.left, binop.right, instrCreator);
  }

  private List<Instruction> visitBinaryInstruction(
      Instruction instruction,
      Argument left,
      Argument right,
      BiFunction<Argument, Register, Instruction> instrCreator) {
    Tuple2<Optional<List<Instruction>>, Register> spillAndRegLeft = null;
    if (!(left instanceof ConstArgument)) {
      spillAndRegLeft = getRegisterForArgument(left);
    }
    Tuple2<Optional<List<Instruction>>, Register> spillAndRegRight = getRegisterForArgument(right);
    List<Instruction> instructions = new ArrayList<>();
    Argument leftArgument = left;
    if (spillAndRegLeft != null) {
      spillAndRegLeft.v1.ifPresent(instructions::addAll);
      leftArgument = spillAndRegLeft.v2;
    }
    spillAndRegRight.v1.ifPresent(instructions::addAll);
    instructions.add(
        instrCreator.apply(leftArgument, spillAndRegRight.v2).firmAndComments(instruction));
    return instructions;
  }

  private List<Instruction> visitUnaryInstruction(
      Instruction instruction, Argument arg, Function<Register, Instruction> instrCreator) {
    Tuple2<Optional<List<Instruction>>, Register> spillAndReg = getRegisterForArgument(arg);
    List<Instruction> instructions = new ArrayList<>();
    spillAndReg.v1.ifPresent(instructions::addAll);
    instructions.add(instrCreator.apply(spillAndReg.v2).firmAndComments(instruction));
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
    for (Register register : evict.registers) {
      evictFromRegister(register).ifPresent(instructions::add);
    }
    return instructions;
  }

  private Optional<Instruction> evictFromRegister(Register register) {
    return evictFromRegister(register, false);
  }

  @Override
  public List<Instruction> visit(Jmp jmp) {
    return ImmutableList.of(jmp);
  }

  /** Returns the current location of the argument and doesn't generate any code. */
  private Argument getCurrentLocation(Argument arg) {
    if (arg instanceof NodeLocation) {
      if (argumentsInRegisters.containsKey(arg)) {
        return argumentsInRegisters.get(arg);
      } else {
        if (arg instanceof ParamLocation) {
          if (((ParamLocation) arg).paramNumber >= Register.methodArgumentQuadRegisters.size()) {
            int inRegs = Register.methodArgumentQuadRegisters.size();
            int paramNum = ((ParamLocation) arg).paramNumber;
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
    List<Register> argRegs = Register.methodArgumentQuadRegisters;
    List<Instruction> instructions = new ArrayList<>();
    instructions.addAll(new Evict(Register.usableRegisters).accept(this));
    // move the register passed argument into the registers
    for (int i = 0; i < Math.min(argRegs.size(), metaCall.methodInfo.paramNumber); i++) {
      Argument arg = metaCall.args.get(i);
      if (getCurrentLocation(arg) == null) {
        throw new NullPointerException(
            String.format("%s: Argument %s doesn't exist", metaCall, arg));
      }
      instructions.add(
          new Mov(getCurrentLocation(arg), argRegs.get(i).ofWidth(arg.width))
              .com(String.format("Move %d.th param into register", i)));
    }
    // the 64 ABI requires the stack to aligned to 16 bytes
    instructions.add(new Push(Register.STACK_POINTER).com("Save old stack pointer"));
    int stackAlignmentDecrement = 0;
    int stackDepthWithProlog = currentStackDepth + 8; // for saving %rbp
    if (stackDepthWithProlog % 16 != 0) { // the stack isn't aligned to 16 Bytes
      // we have to align the stack by decrementing the stack counter
      // this works because we can assume that the base pointer is correctly aligned
      // (induction and the main method is correctly called)
      stackAlignmentDecrement = 16 - stackDepthWithProlog % 16;
      instructions.add(
          new Add(
                  new ConstArgument(Register.Width.Quad, -stackAlignmentDecrement),
                  Register.STACK_POINTER)
              .com("Decrement the stack to ensure alignment"));
    }
    // Use the tmpReg to move the additional parameters on the stack
    for (int i = metaCall.methodInfo.paramNumber - 1; i >= argRegs.size(); i--) {
      Argument arg = metaCall.args.get(i);
      instructions.add(new Push(getCurrentLocation(arg)));
    }
    instructions.add(
        new Call(
                metaCall.methodInfo.hasReturnValue
                    ? metaCall.result.get().width
                    : Register.Width.Quad,
                metaCall.methodInfo.ldName)
            .com("Call the function")
            .firm(metaCall.firm()));
    instructions.add(
        new DeallocStack(
            Math.max(
                    0,
                    metaCall.methodInfo.paramNumber - Register.methodArgumentQuadRegisters.size())
                * 8));
    if (stackAlignmentDecrement != 0) { // we aligned the stack explicitly before
      instructions.add(
          new Add(
                  new ConstArgument(Register.Width.Quad, stackAlignmentDecrement),
                  Register.STACK_POINTER)
              .com("Remove stack alignment"));
    }
    instructions.add(
        new Mov(
                new RegRelativeLocation(Register.Width.Quad, Register.STACK_POINTER, 0),
                Register.STACK_POINTER)
            .com("Restore old stack pointer"));
    if (metaCall.methodInfo.hasReturnValue) {
      Location ret = metaCall.result.get();
      Tuple2<Optional<List<Instruction>>, Register> registerForArgument =
          getRegisterForArgument(ret);
      registerForArgument.v1.ifPresent(instructions::addAll);
      putArgumentIntoRegister(ret, registerForArgument.v2);
      // now we move the return value from the %rax register into the return value's location
      instructions.addAll(
          visit(new Mov(Register.RETURN_REGISTER.ofWidth(ret.width), registerForArgument.v2)));
    }
    return instructions;
  }

  @Override
  public List<Instruction> visit(Mov mov) {
    return visitBinaryInstruction(mov, mov.source, mov.destination, Mov::new);
  }

  @Override
  public List<Instruction> visit(MovFromSmallerToGreater mov) {
    //mov.com("bla");
    return visitBinaryInstruction(
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
  public List<Instruction> visit(MetaLoad load) {
    return visitBinaryInstruction(
        load,
        load.source.address,
        load.destination,
        (l, r) -> {
          RegRelativeLocation source = new RegRelativeLocation(load.width, (Register) l, 0);
          return new Mov(source, r).firm(load.firm()).com("Load " + source.toString());
        });
  }

  @Override
  public List<Instruction> visit(MetaStore store) {
    return visitBinaryInstruction(
        store,
        store.source,
        store.destination.address,
        (l, r) -> {
          RegRelativeLocation destination = new RegRelativeLocation(store.source.width, r, 0);
          return new Mov(l, destination)
              .firm(store.firm())
              .com(String.format("Store into %s", destination));
        });
  }

  @Override
  public List<Instruction> visit(DisableRegisterUsage disableRegisterUsage) {
    usableRegisters.removeAll(disableRegisterUsage.registers);
    freeRegisters.removeAll(disableRegisterUsage.registers);
    return ImmutableList.of();
  }

  @Override
  public List<Instruction> visit(EnableRegisterUsage enableRegisterUsage) {
    for (Register register : enableRegisterUsage.registers) {
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
    List<Register> incorrectRegisters =
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

  private Optional<Instruction> evictFromRegister(Register register, boolean forceSpill) {
    if (forceSpill || usedRegisters.containsKey(register)) {
      Argument arg = usedRegisters.get(register);
      Optional<Instruction> spill =
          genSpillRegisterInstruction(register.ofWidth(arg.width), forceSpill);
      removeArgumentFromRegister(arg);
      freeRegisters.add(register);
      return spill;
    }
    return Optional.empty();
  }

  private List<Instruction> genSpillRegistersInstructions(List<Register> registers) {
    List<Instruction> ret = new ArrayList<>();
    for (Register register : registers) {
      genSpillRegisterInstruction(register).ifPresent(ret::add);
    }
    return ret;
  }

  private Optional<Instruction> genSpillRegisterInstruction(Register register) {
    return genSpillRegisterInstruction(register, false);
  }

  /**
   * It generates an instruction that spills the value stored in the passed register if the value
   * has to be backuped. A value can be dismissed if …
   *
   * <ul>
   *   <li>it's a constant value
   *   <li>it isn't used at a later time (via number in segment)
   * </ul>
   *
   * It doesn't remove the register or the value from any data structure, but creates a stack
   * location if needed.
   */
  private Optional<Instruction> genSpillRegisterInstruction(
      Register register, boolean forceSpilling) {
    Argument argument = usedRegisters.get(register);
    if (!forceSpilling && (argument instanceof ConstArgument)) {
      // we don't have to do anything (see method comment)
      return Optional.empty();
    }
    // we have to spill the value
    if (!hasStackSlotAssigned(argument)) {
      // if we don't have already assigned a stack slot, we create one
      // we use the method because it handles the data structures...
      putArgumentOnStack(argument);
    }
    StackSlot spillSlot = argumentStackSlots.get(argument);
    Instruction spillInstruction =
        new Mov(register.ofWidth(argument.width), spillSlot)
            .com(String.format("Evict %s to %s", register, spillSlot));
    return Optional.of(spillInstruction);
  }

  /**
   * Returns the register that contains the arguments value if the returned instruction were
   * executed before
   *
   * <p>Attention the argument shouldn't be a constant. Use constants directly as an instruction
   * argument.
   *
   * @return (optional spill and reload instructions, register the argument is placed in)
   */
  private Tuple2<Optional<List<Instruction>>, Register> getRegisterForArgument(Argument argument) {
    if (argument instanceof Register) {
      // a valid register was already passed as an argument
      return new Tuple2<Optional<List<Instruction>>, Register>(
          Optional.empty(), (Register) argument);
    }
    if (argumentsInRegisters.containsKey(argument)) {
      // the argument is already in a register, nothing to do...
      return new Tuple2<Optional<List<Instruction>>, Register>(
          Optional.empty(), argumentsInRegisters.get(argument));
    }
    Register.Width width = argument.width;
    List<Instruction> instructions = new ArrayList<>();
    Register register;
    if (needsToEvictRegister()) {
      // we need to evict registers here
      register = chooseRegisterToEvict().ofWidth(width);
      genSpillRegisterInstruction(register.ofWidth(width)).ifPresent(instructions::add);
      Argument priorArgument = usedRegisters.get(register);
      removeArgumentFromRegister(priorArgument);
    } else {
      // we have enough registers
      register = seq(freeRegisters.iterator()).findFirst().get().ofWidth(width);
      freeRegisters.remove(register);
    }
    putArgumentIntoRegister(argument, register);
    Argument currentPlace = null;
    if (hasStackSlotAssigned(argument)) {
      // the argument was evicted once, we have to load its value
      currentPlace = argumentStackSlots.get(argument);
    } else if (argument instanceof ConstArgument) {
      // this is usually a sign that something went wrong, as this case should be been captured early an the constant
      // placed directly into the instruction
      // the only instance where this seems to happen is for Cmp instructions
      currentPlace = argument;
    }
    if (currentPlace != null) {
      instructions.add(
          new Mov(currentPlace, register).com(String.format("Move %s into register", argument)));
    }
    return new Tuple2<Optional<List<Instruction>>, Register>(Optional.of(instructions), register);
  }
}
