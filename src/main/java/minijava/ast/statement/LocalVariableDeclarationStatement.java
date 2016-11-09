package minijava.ast.statement;

import minijava.ast.expression.Expression;
import minijava.ast.type.Type;
import minijava.ast.visitors.BlockStatementVisitor;

public class LocalVariableDeclarationStatement<TRef> implements BlockStatement<TRef> {
  public final Type<TRef> type;
  public final String name;
  public final Expression<TRef> rhs;

  public LocalVariableDeclarationStatement(Type<TRef> type, String name, Expression<TRef> rhs) {
    this.type = type;
    this.name = name;
    this.rhs = rhs;
  }

  @Override
  public <TRet> TRet acceptVisitor(BlockStatementVisitor<TRef, TRet> visitor) {
    return visitor.visitLocalVariableDeclarationStatement(type, name, rhs);
  }
}
