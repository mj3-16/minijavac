package minijava.ast;

import java.util.List;

public class Block<TRef> implements Statement<TRef> {

  public final List<BlockStatement<TRef>> statements;

  public Block(List<BlockStatement<TRef>> statements) {
    this.statements = statements;
  }

  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitBlock(this);
  }
}
