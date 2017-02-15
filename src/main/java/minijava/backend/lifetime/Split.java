package minijava.backend.lifetime;

public class Split<T> {
  public final T before;
  public final T after;

  public Split(T before, T after) {
    this.before = before;
    this.after = after;
  }
}
