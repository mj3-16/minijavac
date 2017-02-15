package minijava.backend.lifetime;

import static minijava.backend.CodeBlockBuilder.newBlock;
import static minijava.backend.registers.AMD64Register.DI;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.beust.jcommander.internal.Lists;
import firm.Relation;
import java.util.ArrayList;
import java.util.List;
import minijava.backend.VirtualRegisterSupply;
import minijava.backend.block.CodeBlock;
import minijava.backend.block.CodeBlock.ExitArity.One;
import minijava.backend.block.CodeBlock.ExitArity.Two;
import minijava.backend.block.CodeBlock.ExitArity.Zero;
import minijava.backend.instructions.Call;
import minijava.backend.instructions.Cmp;
import minijava.backend.instructions.Mov;
import minijava.backend.operands.ImmediateOperand;
import minijava.backend.operands.Operand;
import minijava.backend.operands.OperandWidth;
import minijava.backend.operands.RegisterOperand;
import minijava.backend.registers.Register;
import minijava.backend.registers.VirtualRegister;
import org.junit.Assert;
import org.junit.Test;

public class LifetimeAnalysisTest {
  private final VirtualRegisterSupply supply = new VirtualRegisterSupply();

  @Test
  public void testIfElse() {
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

    List<CodeBlock> codeBlocks = asLinearization(entry, less, greaterEqual, exit);
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

  private static List<CodeBlock> asLinearization(CodeBlock... blocks) {
    List<CodeBlock> ret = new ArrayList<>();
    for (int i = 0; i < blocks.length; i++) {
      CodeBlock block = blocks[i];
      block.linearizedOrdinal = i;
      ret.add(block);
    }
    return ret;
  }

  private static Operand imm(long value) {
    return new ImmediateOperand(OperandWidth.Quad, value);
  }

  private static Operand reg(Register reg) {
    return new RegisterOperand(OperandWidth.Quad, reg);
  }
}
