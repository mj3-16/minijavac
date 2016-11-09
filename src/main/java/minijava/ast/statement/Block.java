package minijava.ast.statement;

import java.util.List;
import minijava.ast.visitors.StatementVisitor;

public class Block<TRef> implements Statement<TRef> {

  public final List<BlockStatement<TRef>> statements;

  public Block(List<BlockStatement<TRef>> statements) {
    this.statements = statements;
  }

  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitBlock(statements);
  }
}
