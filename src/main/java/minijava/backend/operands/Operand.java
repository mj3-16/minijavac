package minijava.backend.operands;

import com.google.common.base.Preconditions;
import firm.nodes.Node;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import minijava.ir.utils.FirmUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Operand for an assembler instruction */
public abstract class Operand {
  public final OperandWidth width;
  /** This is mostly needed for debugging the generated assembly. */
  @Nullable public Node irNode;

  /**
   * Anonymous constructor, e.g. without specifying a firm node as the represented value. Mostly
   * useful for testing and stack manipulations, where there is no corresponding firm node.
   */
  public Operand(OperandWidth width) {
    this.width = width;
    this.irNode = null;
  }

  public Operand(@NotNull Node irNode) {
    Preconditions.checkNotNull("This constructor may only be used with a non-null irNode", irNode);
    this.width = FirmUtils.modeToWidth(irNode.getMode());
    this.irNode = irNode;
  }

  public abstract <T> T match(
      Function<ImmediateOperand, T> matchImm,
      Function<RegisterOperand, T> matchReg,
      Function<MemoryOperand, T> matchMem);

  public void match(
      Consumer<ImmediateOperand> matchImm,
      Consumer<RegisterOperand> matchReg,
      Consumer<MemoryOperand> matchMem) {
    match(
        imm -> {
          matchImm.accept(imm);
          return null;
        },
        reg -> {
          matchReg.accept(reg);
          return null;
        },
        mem -> {
          matchMem.accept(mem);
          return null;
        });
  }

  public abstract Operand withChangedNode(Node irNode);

  public Set<Use> reads(boolean inOutputPosition) {
    return reads(inOutputPosition, false);
  }

  public abstract Set<Use> reads(boolean inOutputPosition, boolean mayBeMemoryAccess);

  public Use writes() {
    return writes(false);
  }

  @Nullable
  public abstract Use writes(boolean mayBeMemoryAccess);
}
