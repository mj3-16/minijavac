package minijava.ir.utils;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Either T or S */
public class Either<S, T> {
  public final Optional<S> s;
  public final Optional<T> t;

  public Either(@Nullable S s, @Nullable T t) {
    assert (s == null) != (t == null);
    this.s = Optional.ofNullable(s);
    this.t = Optional.ofNullable(t);
  }
}
