package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;

public class Block extends Node implements Statement {

  public final List<BlockStatement> statements;

  public Block(List<BlockStatement> statements, SourceRange range) {
    super(range);
    this.statements = statements;
  }

  @Override
  public <T> T acceptVisitor(Statement.Visitor<T> visitor) {
    return visitor.visitBlock(this);
  }
}
