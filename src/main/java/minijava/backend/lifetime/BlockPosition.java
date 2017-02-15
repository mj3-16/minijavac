package minijava.backend.lifetime;

import java.util.Comparator;
import java.util.Objects;
import minijava.backend.block.CodeBlock;
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

  public boolean isUse() {
    return pos % 2 == 1;
  }

  public boolean isDef() {
    return pos % 2 == 0;
  }

  public static BlockPosition definedBy(CodeBlock block, int instructionIndex) {
    instructionIndex++; // account for Phis
    int pos = instructionIndex * 2;
    return new BlockPosition(block, pos);
  }

  public static BlockPosition usedBy(CodeBlock block, int instructionIndex) {
    instructionIndex++; // account for Phis
    int pos = instructionIndex * 2 - 1;
    return new BlockPosition(block, pos);
  }

  public static BlockPosition beginOf(CodeBlock block) {
    return new BlockPosition(block, 0);
  }

  public static BlockPosition endOf(CodeBlock block) {
    // interpret as a use by a pseudo instruction after the last actual instruction
    return usedBy(block, block.instructions.size());
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
