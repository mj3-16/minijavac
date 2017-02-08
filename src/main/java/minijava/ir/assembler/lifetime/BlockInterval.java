package minijava.ir.assembler.lifetime;

import com.google.common.base.Preconditions;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;

public class BlockInterval {

  public final CodeBlock block;
  public final int from; // inclusive, starting at 0 for definitions of PhiFunctions
  public final int to; // inclusive

  public BlockInterval(CodeBlock block, int from, int to) {
    Preconditions.checkArgument(from >= 0, "ConsecutiveRange: from < 0");
    Preconditions.checkArgument(from < to, "ConsecutiveRange: from >= to");
    this.block = block;
    this.from = from;
    this.to = to;
  }

  public BlockInterval from(int from) {
    return new BlockInterval(this.block, from, this.to);
  }

  public BlockInterval to(int to) {
    return new BlockInterval(this.block, this.from, to);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockInterval that = (BlockInterval) o;
    return block.equals(that.block) && from == that.from && to == that.to;
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, from, to);
  }

  @Override
  public String toString() {
    return String.format("[%d, %d]", from, to);
  }

  public BlockInterval intersectionWith(BlockInterval other) {
    if (!block.equals(other.block)) {
      return null;
    }
    if (from > other.to || to < other.from) {
      return null;
    }
    return new BlockInterval(block, Math.max(from, other.from), Math.min(to, other.to));
  }
}
