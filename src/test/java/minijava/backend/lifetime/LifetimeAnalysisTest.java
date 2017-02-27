package minijava.backend.lifetime;

import static minijava.backend.CodeBlockBuilder.asLinearization;
import static minijava.backend.CodeBlockBuilder.newBlock;
import static minijava.backend.operands.OperandUtils.imm;
import static minijava.backend.operands.OperandUtils.reg;
import static minijava.backend.registers.AMD64Register.A;
import static minijava.backend.registers.AMD64Register.DI;
import static minijava.backend.registers.AMD64Register.SI;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.beust.jcommander.internal.Lists;
import firm.Relation;
import java.util.List;
import minijava.backend.CodeBlockBuilder;
import minijava.backend.ExampleProgram;
import minijava.backend.VirtualRegisterSupply;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.CodeBlock.ExitArity.One;
import minijava.backend.block.CodeBlock.ExitArity.Two;
import minijava.backend.block.CodeBlock.ExitArity.Zero;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Cmp;
import minijava.backend.instructions.Mov;
import minijava.backend.registers.VirtualRegister;
import org.junit.Assert;
import org.junit.Test;

public class LifetimeAnalysisTest {
  private final VirtualRegisterSupply supply = new VirtualRegisterSupply();

  @Test
  public void ifElse() {
    VirtualRegister a = supply.next();
    VirtualRegister b = supply.next();
    VirtualRegister c = supply.next();

    CodeBlock entry =
        newBlock("entry")
            .addInstruction(new Mov(imm(1), reg(a)))
            .addInstruction(new Mov(imm(2), reg(b)))
            .addInstruction(new Cmp(reg(b), reg(a)))
            .build();

    CodeBlock less = newBlock("less").build();
    CodeBlock greaterEqual = newBlock("greaterEqual").build();
    CodeBlock exit =
        newBlock("exit")
            .addPhi(reg(c), phi -> phi.from(less, reg(a)).from(greaterEqual, reg(b)).build())
            .addInstruction(new Mov(reg(c), reg(DI)))
            .addInstruction(new Call("print_int", Lists.newArrayList(reg(DI))))
            .build();

    entry.exit = new Two(Relation.Less, less, greaterEqual);
    less.exit = new One(exit);
    greaterEqual.exit = new One(exit);
    exit.exit = new Zero();

    List<CodeBlock> codeBlocks = CodeBlockBuilder.asLinearization(entry, less, greaterEqual, exit);
    LifetimeAnalysisResult result = LifetimeAnalysis.analyse(codeBlocks);

    LifetimeInterval liA = result.virtualIntervals.get(a);
    System.out.println("liA = " + liA);
    LifetimeInterval liB = result.virtualIntervals.get(b);
    System.out.println("liB = " + liB);

    assertAliveIn(liA, "a", entry);
    assertAliveIn(liB, "b", entry);
    assertAliveIn(liA, "a", less);
    assertDeadIn(liB, "b", less); // a hole!
    assertDeadIn(liA, "a", greaterEqual);
    assertAliveIn(liB, "b", greaterEqual);
    assertDeadIn(liA, "a", exit);
    assertDeadIn(liB, "b", exit);
    Assert.assertThat("Propagates hint from Call", liA.toHints, hasItem(DI));
    Assert.assertThat("Propagates hint from Call", liB.toHints, hasItem(DI));
  }

  @Test
  public void loopCountingToFive_invariantDefAliveInWholeBody() {
    ExampleProgram example = ExampleProgram.loopCountingToFive();
    LifetimeAnalysisResult result = LifetimeAnalysis.analyse(example.program);

    // r2 is the constant 1 argument to an Add in the loop body.
    VirtualRegister r2 = example.registers.get(2);
    // footer is the loop footer in the example.
    CodeBlock footer = example.program.get(3);
    Assert.assertTrue(
        "The invariant definition should be alive in the whole loop body",
        result.virtualIntervals.get(r2).covers(BlockPosition.endOf(footer)));
  }

  @Test
  public void callReturnValue_shouldStartAtMov() {
    VirtualRegisterSupply supply = new VirtualRegisterSupply();
    VirtualRegister r0 = supply.next();

    CodeBlock block =
        newBlock("block")
            .addInstruction(new Mov(imm(1), reg(SI)))
            .addInstruction(new Call("print_int", Lists.newArrayList(reg(SI))))
            .addInstruction(new Mov(reg(A), reg(r0)))
            .addInstruction(new Cmp(reg(r0), reg(r0)))
            .build();
    block.exit = new Zero();
    List<CodeBlock> program = asLinearization(block);

    LifetimeAnalysisResult result = LifetimeAnalysis.analyse(program);

    Assert.assertEquals(
        "r0 should be defined by its definition site (duh)",
        BlockPosition.definedBy(block, 2),
        result.virtualIntervals.get(r0).from());
  }

  private static void assertAliveIn(LifetimeInterval li, String name, CodeBlock where) {
    Assert.assertThat(
        String.format("%s is alive in '%s'", name, where.label),
        li.getLifetimeInBlock(where),
        notNullValue());
  }

  private static void assertDeadIn(LifetimeInterval li, String name, CodeBlock where) {
    Assert.assertThat(
        String.format("%s is dead in '%s'", name, where.label),
        li.getLifetimeInBlock(where),
        nullValue());
  }
}
