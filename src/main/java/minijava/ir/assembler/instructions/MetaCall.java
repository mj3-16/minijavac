package minijava.ir.assembler.instructions;

import java.util.Collections;
import java.util.List;
import minijava.ir.utils.MethodInformation;

/**
 * Meta method call instruction
 *
 * <p>// the first six arguments are passed via registers // we don't have to backup them as we only
 * use their copies somewhere on the stack // that we created at the beginning of the methods
 * assembly // the 64 ABI requires the stack to aligned to 16 bytes block.add(new
 * Push(Register.STACK_POINTER).com("Save old stack pointer")); block.add( new Push(new
 * RegRelativeLocation(Register.STACK_POINTER, 0)) .com("Save the stack pointer again because of
 * alignment issues")); block.add( new And(new ConstArgument(-0x10), Register.STACK_POINTER)
 * .com("Align the stack pointer to 16 bytes")); for (int i = args.size() - 1; i >=
 * Register.methodArgumentQuadRegisters.size(); i--) { block.add(new Push(args.get(i))); }
 * block.add(new Call(getMethodLdName(node)).com("Call the external function").firm(node));
 * block.add( new DeallocStack( Math.max(0, args.size() -
 * Register.methodArgumentQuadRegisters.size()) * 8)); block.add( new Mov(new
 * RegRelativeLocation(Register.STACK_POINTER, 8), Register.STACK_POINTER) .com("Restore old stack
 * pointer"));
 */
public class MetaCall extends Instruction {

  public final List<Argument> args;
  public final MethodInformation methodInfo;

  public MetaCall(List<Argument> args, MethodInformation methodInfo) {
    this.args = Collections.unmodifiableList(args);
    this.methodInfo = methodInfo;
  }

  @Override
  public Type getType() {
    return Type.META_CALL;
  }
}
