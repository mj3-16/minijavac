package minijava.ast.visitors;

import minijava.ast.expression.Expression;
import minijava.ast.type.Type;

public interface BlockStatementVisitor<TRef, TRet> extends StatementVisitor<TRef, TRet> {

  TRet visitLocalVariableDeclarationStatement(Type<TRef> type, String name, Expression<TRef> rhs);
}
