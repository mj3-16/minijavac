package minijava.ir.assembler.lifetime;

import java.util.Comparator;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;
import org.jetbrains.annotations.NotNull;

public class BlockPosition implements Comparable<BlockPosition> {

  private static Comparator<BlockPosition> COMPARATOR =
      Comparator.comparingInt((BlockPosition bp) -> bp.block.linearizedOrdinal)
          .thenComparingInt(bp -> bp.useDefIndex);
  public final CodeBlock block;
  public final int useDefIndex;

  public BlockPosition(CodeBlock block, int useDefIndex) {
    this.block = block;
    this.useDefIndex = useDefIndex;
  }

  public static BlockPosition fromBlockIntervalStart(BlockInterval interval) {
    return new BlockPosition(interval.block, interval.from);
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
    return useDefIndex == that.useDefIndex && Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, useDefIndex);
  }

  @Override
  public int compareTo(@NotNull BlockPosition other) {
    return COMPARATOR.compare(this, other);
  }
}
