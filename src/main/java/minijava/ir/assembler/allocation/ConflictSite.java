package minijava.ir.assembler.allocation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import java.util.Comparator;
import java.util.Objects;
import minijava.ir.assembler.lifetime.BlockPosition;
import org.jetbrains.annotations.Nullable;

class ConflictSite implements Comparable<ConflictSite> {

  public static Comparator<ConflictSite> BY_POSITION =
      comparing(u -> u.position, nullsLast(naturalOrder()));
  private static final ConflictSite NEVER = new ConflictSite(null);
  @Nullable private final BlockPosition position;

  public ConflictSite(BlockPosition position) {
    this.position = position;
  }

  public static ConflictSite never() {
    return NEVER;
  }

  public static ConflictSite at(BlockPosition position) {
    checkArgument(position != null, "The specified conflicting position must be non-null.");
    return new ConflictSite(position);
  }

  /** Where null means never. */
  public static ConflictSite atOrNever(BlockPosition position) {
    return position != null ? new ConflictSite(position) : never();
  }

  public boolean doesConflictAtAll() {
    return position != null;
  }

  public BlockPosition conflictingPosition() {
    checkNotNull(position, "The conflict site does never conflict, so there's no position");
    return position;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ConflictSite that = (ConflictSite) o;
    return Objects.equals(position, that.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(position);
  }

  @Override
  public int compareTo(ConflictSite other) {
    return BY_POSITION.compare(this, other);
  }

  @Override
  public String toString() {
    if (doesConflictAtAll()) {
      return "ConflicSite.at(" + position + ')';
    } else {
      return "ConflictSite.never()";
    }
  }
}
