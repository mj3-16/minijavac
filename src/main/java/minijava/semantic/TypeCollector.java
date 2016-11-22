package minijava.semantic;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import minijava.ast.*;
import minijava.ast.Class;

class TypeCollector implements Program.Visitor<SymbolTable<BasicType>> {

  private static final Set<BuiltinType> BUILTIN_TYPES =
      ImmutableSet.of(BuiltinType.INT, BuiltinType.BOOLEAN, BuiltinType.VOID);

  @Override
  public SymbolTable<BasicType> visitProgram(Program that) {
    SymbolTable<BasicType> symtab = new SymbolTable<>();
    symtab.enterScope();
    // builtin types are just there
    for (BuiltinType b : BUILTIN_TYPES) {
      symtab.insert(b.name(), b);
    }
    for (Class c : that.declarations) {
      Optional<BasicType> sameType = symtab.lookup(c.name());
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
