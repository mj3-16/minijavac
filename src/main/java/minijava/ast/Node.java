package minijava.ast;

import minijava.utils.SourceCodeReferable;
import minijava.utils.SourceRange;

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
