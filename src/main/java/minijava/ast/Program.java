package minijava.ast;

import java.util.List;

public class Program<TRef> {
  public final List<ClassDeclaration> declarations;

  public Program(List<ClassDeclaration> declarations) {
    this.declarations = declarations;
  }
}
