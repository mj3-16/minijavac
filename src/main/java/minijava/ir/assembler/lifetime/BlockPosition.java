package minijava.ir.assembler.lifetime;

import java.util.Comparator;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;
import org.jetbrains.annotations.NotNull;

public class BlockPosition implements Comparable<BlockPosition> {

  private static Comparator<BlockPosition> COMPARATOR =
      Comparator.comparingInt((BlockPosition bp) -> bp.block.linearizedOrdinal)
          .thenComparingInt(bp -> bp.pos);
  public final CodeBlock block;
  public final int pos;

  public BlockPosition(CodeBlock block, int pos) {
    this.block = block;
    this.pos = pos;
  }

  static int definedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2;
  }

  static int usedBy(int instructionIndex) {
    instructionIndex++; // account for Phis
    return instructionIndex * 2 - 1;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockPosition that = (BlockPosition) o;
    return pos == that.pos && Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, pos);
  }

  @Override
  public String toString() {
    return block.label + ":" + pos;
  }

  @Override
  public int compareTo(@NotNull BlockPosition other) {
    return COMPARATOR.compare(this, other);
  }
}
