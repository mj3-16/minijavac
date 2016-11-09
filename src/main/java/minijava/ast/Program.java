package minijava.ast;

import java.util.List;
import minijava.ast.visitors.ProgramVisitor;

public class Program<TRef> {
  public final List<ClassDeclaration<TRef>> declarations;

  public Program(List<ClassDeclaration<TRef>> declarations) {
    this.declarations = declarations;
  }

  public <TRet> TRet acceptVisitor(ProgramVisitor<TRef, TRet> visitor) {
    return visitor.visitProgram(declarations);
  }
}
