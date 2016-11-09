package minijava.ast.visitors;

import java.util.List;
import minijava.ast.ClassDeclaration;

public interface ProgramVisitor<TRef, TReturn> {

  TReturn visitProgram(List<ClassDeclaration<TRef>> declarations);
}
