package minijava.ast.statement;

import minijava.ast.visitors.StatementVisitor;

public class EmptyStatement<TRef> implements Statement<TRef> {
  @Override
  public <TRet> TRet acceptVisitor(StatementVisitor<TRef, TRet> visitor) {
    return visitor.visitEmptyStatement();
  }
}
