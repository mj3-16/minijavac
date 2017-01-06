package minijava.ir.assembler.allocator;

import static org.jooq.lambda.Seq.seq;

import com.google.common.collect.ImmutableList;
import java.util.*;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import minijava.ir.assembler.SimpleNodeAllocator;
import minijava.ir.assembler.block.LinearCodeSegment;
import minijava.ir.assembler.instructions.*;
import minijava.ir.assembler.location.*;
import minijava.ir.utils.MethodInformation;
import org.jooq.lambda.tuple.Tuple2;

/**
 * This is a basic implementation of a register allocator, that allocates {@link
 * minijava.ir.assembler.location.NodeLocation} instances in registers and the heap.
 *
 * <p>It implements the linear scan algorithm →
 * http://web.cs.ucla.edu/~palsberg/course/cs132/linearscan.pdf
 */
public class BasicRegAllocator implements InstructionVisitor<List<Instruction>> {

  protected final MethodInformation methodInfo;
  protected final SimpleNodeAllocator nodeAllocator;
  protected final LinearCodeSegment code;

  private Map<Argument, StackSlot> argumentStackSlots;
  /**
   * Empty optionals are equivalent to "there was a stack slot assigned here and was later removed,
   * but there are stack slots that have a bigger offset)
   */
  private List<Optional<StackSlot>> assignedStackSlots;

  private Map<Register, Argument> usedRegisters;
  private Map<Argument, Register> argumentsInRegisters;
  private TreeSet<Argument> sortedArgumentsInRegisters;
  private Set<Register> freeRegisters;
  private int maxStackHeight;
  private int currentStackHeight;
  private int currentInstructionNumber;

  public BasicRegAllocator(
      MethodInformation methodInfo, LinearCodeSegment code, SimpleNodeAllocator nodeAllocator) {
    this.methodInfo = methodInfo;
    this.code = code;
    this.nodeAllocator = nodeAllocator;
    this.maxStackHeight = 0;
    this.currentStackHeight = 0;
    this.currentInstructionNumber = 0;
    this.assignedStackSlots = new ArrayList<>();
    this.argumentStackSlots = new HashMap<>();
    this.usedRegisters = new HashMap<>();
    this.freeRegisters = new HashSet<>();
    this.argumentsInRegisters = new HashMap<>();
    this.sortedArgumentsInRegisters =
        new TreeSet<>(
            (o1, o2) ->
                Integer.compare(
                    o1.instructionRelations.getLastInstruction().getNumberInSegment(),
                    o2.instructionRelations.getLastInstruction().getNumberInSegment()));
    // setup the method parameters
    List<Register> argRegs = Register.methodArgumentQuadRegisters;
    for (int i = 0; i < Math.min(argRegs.size(), methodInfo.paramNumber); i++) {
      putArgumentIntoRegister(nodeAllocator.paramLocations.get(i), argRegs.get(i));
    }
    for (int i = argRegs.size(); i < methodInfo.paramNumber; i++) {
      ParamLocation paramLocation = nodeAllocator.paramLocations.get(i);
      putArgumentOnStack(paramLocation, 8);
    }
    this.freeRegisters =
        seq(Register.usableRegisters).removeAll(argRegs).collect(Collectors.toSet());
  }

  private void putArgumentIntoRegister(Argument argument, Register register) {
    register = register.ofWidth(argument.width);
    this.argumentsInRegisters.put(argument, register);
    this.usedRegisters.put(register, argument);
    this.sortedArgumentsInRegisters.add(argument);
  }

  private void removeArgumentFromRegister(Argument argument) {
    Register reg = argumentsInRegisters.get(argument);
    this.usedRegisters.remove(reg);
    this.argumentsInRegisters.remove(argument);
    this.sortedArgumentsInRegisters.remove(argument);
  }

  private void putArgumentOnStack(Argument argument, int stackIncrement) {
    if (!hasStackSlotAsigned(argument)) {
      StackSlot slot = new StackSlot(argument.width, currentStackHeight);
      slot.setComment(argument.getComment());
      argumentStackSlots.put(argument, slot);
      assignedStackSlots.add(Optional.of(slot));
      currentStackHeight += stackIncrement;
      if (currentStackHeight > maxStackHeight) {
        maxStackHeight = currentStackHeight;
      }
    }
  }

  private void putArgumentOnStack(Argument argument) {
    putArgumentOnStack(argument, argument.width.sizeInBytes);
  }

  private void removeArgumentFromStack(Argument argument) {
    if (hasStackSlotAsigned(argument)) {
      final StackSlot slot = argumentStackSlots.get(argument);
      argumentStackSlots.remove(argument);
      assignedStackSlots.replaceAll(
          s -> s.isPresent() && s.get().equals(slot) ? Optional.empty() : s);
      resizeStack();
    }
  }

  private void resizeStack() {
    for (int i = assignedStackSlots.size() - 1; i >= 0; i--) {
      Optional<StackSlot> s = assignedStackSlots.get(i);
      if (s.isPresent()) {
        maxStackHeight = s.get().offset + s.get().width.sizeInBytes;
        break;
      } else {
        assignedStackSlots.remove(i);
      }
    }
  }

  private Register chooseRegisterToEvict() {
    Argument evictedArgument = sortedArgumentsInRegisters.last();
    return argumentsInRegisters.get(evictedArgument);
  }

  private boolean needsToEvictRegister() {
    return freeRegisters.isEmpty();
  }

  public LinearCodeSegment process() {
    List<LinearCodeSegment.InstructionOrString> ret = new ArrayList<>();
    for (LinearCodeSegment.InstructionOrString instructionOrString : code) {
      if (instructionOrString.instruction.isPresent()) {
        Instruction instruction = instructionOrString.instruction.get();
        currentInstructionNumber = instruction.getNumberInSegment();
        List<Instruction> replacement = instruction.accept(this);
        replacement.stream().map(LinearCodeSegment.InstructionOrString::new).forEach(ret::add);
      } else {
        ret.add(instructionOrString);
      }
    }
    return new LinearCodeSegment(ret, code.getComments());
  }

  /**
   * Returns the register that contains the arguments value if the returned instruction were
   * executed before
   *
   * @return (optional spill and reload instructions, register the argument is placed in)
   */
  private Tuple2<Optional<List<Instruction>>, Register> getRegisterForArgument(Argument argument) {
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
      register = chooseRegisterToEvict();
      genSpillRegisterInstruction(register.ofWidth(width)).ifPresent(instructions::add);
      Argument priorArgument = usedRegisters.get(register);
      removeArgumentFromRegister(priorArgument);
    } else {
      // we have enough registers
      register = seq(freeRegisters).findFirst().get().ofWidth(width);
    }
    putArgumentIntoRegister(argument, register);
    if (hasStackSlotAsigned(argument)) {
      // the argument was evicted once, we have to load its value
      instructions.add(new Mov(argumentStackSlots.get(argument), register));
    }
    return new Tuple2<Optional<List<Instruction>>, Register>(Optional.of(instructions), register);
  }

  private Optional<Instruction> evictFromRegister(Register register) {
    if (usedRegisters.containsKey(register)) {
      Argument arg = usedRegisters.get(register);
      Optional<Instruction> spill = genSpillRegisterInstruction(register.ofWidth(arg.width));
      removeArgumentFromRegister(arg);
      freeRegisters.add(register);
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
  private Optional<Instruction> genSpillRegisterInstruction(Register register) {
    Argument argument = usedRegisters.get(register);
    if (argument instanceof ConstArgument
        || currentInstructionNumber >= argument.instructionRelations.getLastInstructionNumber()) {
      // we don't have to do anything (see method comment)
      return Optional.empty();
    }
    // we have to spill the value
    if (!hasStackSlotAsigned(argument)) {
      // if we don't have already assigned a stack slot, we create one
      // we use the method because it handles the data structures...
      putArgumentOnStack(argument);
    }
    StackSlot spillSlot = argumentStackSlots.get(argument);
    Instruction spillInstruction = new Mov(register.ofWidth(argument.width), spillSlot);
    return Optional.of(spillInstruction);
  }

  private boolean hasStackSlotAsigned(Argument argument) {
    return argumentStackSlots.containsKey(argument);
  }

  @Override
  public List<Instruction> visit(MethodPrologue prologue) {
    return ImmutableList.of(
        new Push(Register.BASE_POINTER).com("Backup old base pointer"),
        new Mov(Register.STACK_POINTER, Register.BASE_POINTER),
        new AllocStack(maxStackHeight));
  }

  @Override
  public List<Instruction> visit(Add add) {
    return visitBinaryInstruction(add, Add::new);
  }

  private List<Instruction> visitBinaryInstruction(
      BinaryInstruction binop, BiFunction<Register, Register, Instruction> instrCreator) {
    return visitBinaryInstruction(binop.left, binop.right, instrCreator);
  }

  private List<Instruction> visitBinaryInstruction(
      Argument left, Argument right, BiFunction<Register, Register, Instruction> instrCreator) {
    Tuple2<Optional<List<Instruction>>, Register> spillAndRegLeft = getRegisterForArgument(left);
    Tuple2<Optional<List<Instruction>>, Register> spillAndRegRight = getRegisterForArgument(right);
    List<Instruction> instructions = new ArrayList<>();
    spillAndRegLeft.v1.ifPresent(instructions::addAll);
    spillAndRegRight.v1.ifPresent(instructions::addAll);
    instructions.add(instrCreator.apply(spillAndRegLeft.v2, spillAndRegRight.v2));
    return instructions;
  }

  private List<Instruction> visitUnaryInstruction(
      Argument arg, Function<Register, Instruction> instrCreator) {
    Tuple2<Optional<List<Instruction>>, Register> spillAndReg = getRegisterForArgument(arg);
    List<Instruction> instructions = new ArrayList<>();
    spillAndReg.v1.ifPresent(instructions::addAll);
    instructions.add(instrCreator.apply(spillAndReg.v2));
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
        return argumentStackSlots.get(arg);
      }
    }
    // we don't have to care about "real" locations here
    return arg;
  }

  @Override
  public List<Instruction> visit(MetaCall metaCall) {
    List<Register> argRegs = Register.methodArgumentQuadRegisters;
    // evict all registers
    for (Register usableRegister : Register.usableRegisters) {
      evictFromRegister(usableRegister);
    }
    List<Instruction> instructions = new ArrayList<>();
    // move the register passed argument into the registers
    for (int i = 0; i < Math.min(argRegs.size(), metaCall.methodInfo.paramNumber); i++) {
      Argument arg = metaCall.args.get(i);
      instructions.add(new Mov(getCurrentLocation(arg), argRegs.get(i)));
    }
    // the 64 ABI requires the stack to aligned to 16 bytes
    instructions.add(new Push(Register.STACK_POINTER).com("Save old stack pointer"));
    instructions.add(
        new Push(new RegRelativeLocation(Register.Width.Quad, Register.STACK_POINTER, 0))
            .com("Save the stack pointer again because of alignment issues"));
    instructions.add(
        new And(new ConstArgument(Register.Width.Quad, -0x10), Register.STACK_POINTER)
            .com("Align the stack pointer to 16 bytes"));
    // Use the tmpReg to move the additional parameters on the stack
    for (int i = argRegs.size(); i < metaCall.methodInfo.paramNumber; i++) {
      Argument arg = metaCall.args.get(i);
      instructions.add(new Push(getCurrentLocation(arg)));
    }
    instructions.add(
        new Call(metaCall.methodInfo.ldName)
            .com("Call the external function")
            .firm(metaCall.firm()));
    instructions.add(
        new DeallocStack(
            Math.max(
                    0,
                    metaCall.methodInfo.paramNumber - Register.methodArgumentQuadRegisters.size())
                * 8));
    instructions.add(
        new Mov(
                new RegRelativeLocation(Register.Width.Quad, Register.STACK_POINTER, 8),
                Register.STACK_POINTER)
            .com("Restore old stack pointer"));
    if (metaCall.methodInfo.hasReturnValue) {
      Argument ret = metaCall.result.get();
      // the return value is in the RAX register
      putArgumentIntoRegister(ret, Register.RETURN_REGISTER);
    }
    return instructions;
  }

  @Override
  public List<Instruction> visit(Mov mov) {
    return visitBinaryInstruction(mov.source, mov.destination, Mov::new);
  }

  @Override
  public List<Instruction> visit(Mul mul) {
    return visitBinaryInstruction(mul, Mul::new);
  }

  @Override
  public List<Instruction> visit(Neg neg) {
    return visitUnaryInstruction(neg.arg, Neg::new);
  }

  @Override
  public List<Instruction> visit(Pop pop) {
    return visitUnaryInstruction(pop.arg, Pop::new);
  }

  @Override
  public List<Instruction> visit(Push push) {
    return visitUnaryInstruction(push.arg, Push::new);
  }

  @Override
  public List<Instruction> visit(Ret ret) {
    return ImmutableList.of(ret);
  }

  @Override
  public List<Instruction> visit(final minijava.ir.assembler.instructions.Set set) {
    return visitUnaryInstruction(
        set.arg, arg -> new minijava.ir.assembler.instructions.Set(set.relation, arg));
  }

  @Override
  public List<Instruction> visit(Sub sub) {
    return visitBinaryInstruction(sub, Sub::new);
  }

  @Override
  public List<Instruction> visit(MetaLoad load) {
    return visitBinaryInstruction(
        load.source.address,
        load.destination,
        (l, r) -> {
          RegRelativeLocation source = new RegRelativeLocation(load.destination.width, l, 0);
          return new Mov(source, r);
        });
  }

  @Override
  public List<Instruction> visit(MetaStore store) {
    return visitBinaryInstruction(
        store.source,
        store.destination.address,
        (l, r) -> {
          RegRelativeLocation destination = new RegRelativeLocation(store.source.width, r, 0);
          return new Mov(l, destination);
        });
  }
}
