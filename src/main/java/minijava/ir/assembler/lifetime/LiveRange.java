package minijava.ir.assembler.lifetime;

import com.google.common.base.Preconditions;
import java.util.Objects;
import minijava.ir.assembler.block.CodeBlock;

public class LiveRange {

  public final CodeBlock block;
  public final int from; // inclusive, starting at 0 for definitions of PhiFunctions
  public final int to; // inclusive

  public LiveRange(CodeBlock block, int from, int to) {
    Preconditions.checkArgument(from >= 0, "ConsecutiveRange: from < 0");
    Preconditions.checkArgument(from <= to, "ConsecutiveRange: from > to");
    this.block = block;
    this.from = from;
    this.to = to;
  }

  static LiveRange everywhere(CodeBlock block) {
    int from = 0;
    int to = BlockPosition.usedBy(block.instructions.size());
    return new LiveRange(block, from, to);
  }

  public LiveRange from(int from) {
    return new LiveRange(this.block, from, this.to);
  }

  public LiveRange to(int to) {
    return new LiveRange(this.block, this.from, to);
  }

  public BlockPosition fromPosition() {
    return new BlockPosition(block, from);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LiveRange that = (LiveRange) o;
    return block.equals(that.block) && from == that.from && to == that.to;
  }

  @Override
  public int hashCode() {
    return Objects.hash(block, from, to);
  }

  @Override
  public String toString() {
    return String.format("%s:[%d, %d]", block.label, from, to);
  }

  public LiveRange intersectionWith(LiveRange other) {
    if (!block.equals(other.block)) {
      return null;
    }
    if (from > other.to || to < other.from) {
      return null;
    }
    return new LiveRange(block, Math.max(from, other.from), Math.min(to, other.to));
  }

  public boolean contains(BlockPosition position) {
    return position.block.equals(block) && from <= position.pos && position.pos <= to;
  }
}
