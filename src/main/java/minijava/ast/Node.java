package minijava.ast;

import minijava.util.SourceCodeReferable;
import minijava.util.SourceRange;

/** This abstract class stores information that is common between all non-abstract AST nodes. */
abstract class Node implements SourceCodeReferable {

  private final SourceRange range;

  Node(SourceRange range) {
    this.range = range;
  }

  @Override
  public SourceRange range() {
    return range;
  }
}
