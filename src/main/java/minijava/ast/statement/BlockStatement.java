package minijava.ast.statement;

import minijava.ast.visitors.BlockStatementVisitor;

public interface BlockStatement<TRef> {
  <TRet> TRet acceptVisitor(BlockStatementVisitor<TRef, TRet> visitor);
}
