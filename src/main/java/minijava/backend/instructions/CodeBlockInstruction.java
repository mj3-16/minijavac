package minijava.backend.instructions;

import java.util.List;
import minijava.backend.operands.Operand;

/**
 * This is an intermediate class for Instructions which can be added to a CodeBlock (which is
 * somewhat IR-ish). Why? We want to disallow adding instructions like Jmp, Pop, and PhiFunction to
 * a CodeBlock, as they are only added in a separate lowering step after instruction selection.
 * CodeBlock represents those more coherently in its phis or exit fields, while spilling
 * instructions are inserted when we produce actual instruction lists after register allocation.
 *
 * <p>This is actually not the best design: An intersection type with an interface CodeBlockHoldable
 * or something would be better, but we can't express List&lt;? extends Instruction &
 * CodeBlockHoldable&gt;.
 */
// Making a separate comment as googleJavaFormat screws up my ASCII art.
// This is what the hierarchy looks like approximately:
//                      Instruction
//                     /           \
//                    /           Jmp, Label, PhiFunction, Pop, ...
//                   /
//          CodeBlockInstruction
//              |               \
//     TwoAddressInstruction     \
//              |                 \
//       Add, And, IMul, Sub     Call, Ret, Cmp, ...
public abstract class CodeBlockInstruction extends Instruction {
  protected CodeBlockInstruction(List<Operand> inputs, List<Operand> outputs) {
    super(inputs, outputs);
  }

  public abstract void accept(Visitor visitor);

  @Override
  public void accept(Instruction.Visitor visitor) {
    accept((Visitor) visitor);
  }

  public interface Visitor {
    default void visit(Add add) {}

    default void visit(And and) {}

    default void visit(Call call) {}

    default void visit(Cltd cltd) {}

    default void visit(Cmp cmp) {}

    default void visit(Enter enter) {}

    default void visit(IDiv idiv) {}

    default void visit(IMul imul) {}

    default void visit(Leave leave) {}

    default void visit(Mov mov) {}

    default void visit(Neg neg) {}

    default void visit(Setcc setcc) {}

    default void visit(Sub sub) {}

    default void visit(Test test) {}
  }
}
