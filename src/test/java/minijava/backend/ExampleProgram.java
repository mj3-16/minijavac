package minijava.backend;

import static minijava.backend.CodeBlockBuilder.asLinearization;
import static minijava.backend.CodeBlockBuilder.newBlock;
import static minijava.backend.OperandUtils.imm;
import static minijava.backend.OperandUtils.reg;
import static minijava.backend.registers.AMD64Register.DI;

import com.google.common.collect.Lists;
import firm.Relation;
import java.util.List;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.CodeBlock.ExitArity.One;
import minijava.backend.block.CodeBlock.ExitArity.Two;
import minijava.backend.block.CodeBlock.ExitArity.Zero;
import minijava.backend.instructions.Add;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Cmp;
import minijava.backend.instructions.IMul;
import minijava.backend.instructions.Mov;
import minijava.backend.registers.VirtualRegister;

public class ExampleProgram {
  public final List<VirtualRegister> registers;
  public final List<CodeBlock> program;

  private ExampleProgram(List<VirtualRegister> registers, List<CodeBlock> program) {
    this.registers = registers;
    this.program = program;
  }

  public static ExampleProgram loopCountingToFive() {
    VirtualRegisterSupply supply = new VirtualRegisterSupply();
    VirtualRegister r0 = supply.next();
    VirtualRegister r1 = supply.next();
    VirtualRegister r2 = supply.next();
    VirtualRegister r3 = supply.next();
    VirtualRegister r4 = supply.next();

    CodeBlock entry =
        newBlock("entry")
            .addInstruction(new Mov(imm(5), reg(r0)))
            .addInstruction(new Mov(imm(0), reg(r1)))
            .addInstruction(new Mov(imm(1), reg(r2)))
            .build();

    // We need to reference the loop footer in the PhiFunction, so apologies for the weird order.
    CodeBlock loopTrampoline = newBlock("loopTrampoline").build();

    CodeBlock footer =
        newBlock("footer")
            .addInstruction(new Mov(reg(r3), reg(r4)))
            .addInstruction(new Add(reg(r2), reg(r4)))
            .build();

    CodeBlock header =
        newBlock("header")
            .addPhi(reg(r3), builder -> builder.from(entry, reg(r1)).from(footer, reg(r4)).build())
            .addLoopBody(footer, loopTrampoline)
            .addInstruction(new Cmp(reg(r3), reg(r0)))
            .build();

    CodeBlock exitTrampoline = newBlock("exitTrampoline").build();

    CodeBlock exit =
        newBlock("exit")
            .addInstruction(new Mov(reg(r3), reg(DI)))
            .addInstruction(new Call("print_int", Lists.newArrayList(reg(DI))))
            .build();

    entry.exit = new One(header);
    header.exit = new Two(Relation.Greater, loopTrampoline, exitTrampoline);
    loopTrampoline.exit = new One(footer);
    footer.exit = new One(header);
    exitTrampoline.exit = new One(exit);
    exit.exit = new Zero();

    return new ExampleProgram(
        Lists.newArrayList(r0, r1, r2, r3, r4),
        asLinearization(entry, header, loopTrampoline, footer, exitTrampoline, exit));
  }

  public static ExampleProgram doubleFib() {
    VirtualRegisterSupply supply = new VirtualRegisterSupply();
    VirtualRegister r0 = supply.next(); // 5
    VirtualRegister r1 = supply.next(); // i before loop
    VirtualRegister r2 = supply.next(); // 0
    VirtualRegister r3 = supply.next(); // i in header
    VirtualRegister r4 = supply.next(); // i in footer
    VirtualRegister r5 = supply.next(); // j before loop
    VirtualRegister r6 = supply.next(); // j in header
    VirtualRegister r7 = supply.next(); // j in footer
    VirtualRegister r8 = supply.next(); // k before loop
    VirtualRegister r9 = supply.next(); // k in header
    VirtualRegister r10 = supply.next(); // k in footer

    CodeBlock entry =
        newBlock("entry")
            .addInstruction(new Mov(imm(5), reg(r0)))
            .addInstruction(new Mov(imm(0), reg(r1)))
            .addInstruction(new Mov(imm(1), reg(r2)))
            .addInstruction(new Mov(imm(1), reg(r5)))
            .addInstruction(new Mov(imm(1), reg(r8)))
            .build();

    // We need to reference the loop footer in the PhiFunction, so apologies for the weird order.
    CodeBlock loopTrampoline = newBlock("loopTrampoline").build();

    CodeBlock footer =
        newBlock("footer")
            .addInstruction(new Mov(reg(r3), reg(r4)))
            .addInstruction(new Add(reg(r2), reg(r4)))
            .addInstruction(new Mov(reg(r6), reg(r7)))
            .addInstruction(new IMul(reg(r4), reg(r7)))
            .addInstruction(new Mov(reg(r9), reg(r10)))
            .addInstruction(new IMul(reg(r4), reg(r10)))
            .build();

    CodeBlock header =
        newBlock("header")
            .addPhi(reg(r3), builder -> builder.from(entry, reg(r1)).from(footer, reg(r4)).build())
            .addPhi(reg(r6), builder -> builder.from(entry, reg(r5)).from(footer, reg(r7)).build())
            .addPhi(reg(r9), builder -> builder.from(entry, reg(r8)).from(footer, reg(r10)).build())
            .addLoopBody(footer, loopTrampoline)
            .addInstruction(new Cmp(reg(r3), reg(r0)))
            .build();

    CodeBlock exitTrampoline = newBlock("exitTrampoline").build();

    CodeBlock exit =
        newBlock("exit")
            .addInstruction(new Mov(reg(r6), reg(DI)))
            .addInstruction(new Call("print_int", Lists.newArrayList(reg(DI))))
            .addInstruction(new Mov(reg(r9), reg(DI)))
            .addInstruction(new Call("print_int", Lists.newArrayList(reg(DI))))
            .build();

    entry.exit = new One(header);
    header.exit = new Two(Relation.Greater, loopTrampoline, exitTrampoline);
    loopTrampoline.exit = new One(footer);
    footer.exit = new One(header);
    exitTrampoline.exit = new One(exit);
    exit.exit = new Zero();

    return new ExampleProgram(
        Lists.newArrayList(r0, r1, r2, r3, r4),
        asLinearization(entry, header, loopTrampoline, footer, exitTrampoline, exit));
  }
}
