package minijava.semantic;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import minijava.ast.*;
import minijava.ast.Class;

class TypeCollector implements Program.Visitor<SymbolTable<Definition>> {

  private static final Set<BuiltinType> BASIC_TYPES =
      ImmutableSet.of(BuiltinType.INT, BuiltinType.BOOLEAN, BuiltinType.VOID);

  @Override
  public SymbolTable<Definition> visitProgram(Program that) {
    SymbolTable<Definition> symtab = new SymbolTable<>();
    symtab.enterScope();
    // basic types are just there
    for (BuiltinType b : BASIC_TYPES) {
      symtab.insert(b.name(), b);
    }
    for (Class c : that.declarations) {
      Optional<Definition> sameType = symtab.lookup(c.name());
      if (sameType.isPresent()) {
        throw new SemanticError(
            c.range(),
            "Type with name " + c.name() + " is already defined at " + sameType.get().range());
      }
      symtab.insert(c.name(), c);
    }
    return symtab;
  }
}
