package minijava.backend.lifetime;

public class UseSite {
  public final BlockPosition position;
  public final boolean mayBeReplacedByMemoryAccess;

  public UseSite(BlockPosition position, boolean mayBeReplacedByMemoryAccess) {
    this.position = position;
    this.mayBeReplacedByMemoryAccess = mayBeReplacedByMemoryAccess;
  }

  @Override
  public String toString() {
    return position + (mayBeReplacedByMemoryAccess ? "(r/m)" : "(r)");
  }
}
