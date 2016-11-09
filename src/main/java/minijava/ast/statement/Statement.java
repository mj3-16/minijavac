package minijava.ast.statement;

import minijava.ast.visitors.BlockStatementVisitor;
import minijava.ast.visitors.StatementVisitor;

public interface Statement<TRef> extends BlockStatement<TRef> {
  <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor);

  default <TRet> TRet acceptVisitor(BlockStatementVisitor<TRef, TRet> visitor) {
    return acceptVisitor((StatementVisitor<TRef, TRet>) visitor);
  }
}
