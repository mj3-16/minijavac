package minijava.ast;

import java.util.List;
import minijava.util.SourceRange;

public class Block<TRef> extends Statement.Base<TRef> {

  public final List<BlockStatement<TRef>> statements;

  public Block(List<BlockStatement<TRef>> statements, SourceRange range) {
    super(range);
    this.statements = statements;
  }

  @Override
  public <TRet> TRet acceptVisitor(Statement.Visitor<? super TRef, TRet> visitor) {
    return visitor.visitBlock(this);
  }
}
