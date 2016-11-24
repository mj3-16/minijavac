package minijava.util;

/** Objects of this type refer to the original MiniJava source code. */
public interface SourceCodeReferable {

  /** Returns a {@link SourceRange} to which this objects refers to in the original source code */
  SourceRange range();
}
