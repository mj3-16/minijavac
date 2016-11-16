package minijava.semantic;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import minijava.ast.*;
import minijava.ast.Class;

class TypeCollector implements Program.Visitor<Nameable, SymbolTable> {

  private static final Set<BasicType> BASIC_TYPES =
      ImmutableSet.of(BasicType.INT, BasicType.BOOLEAN, BasicType.VOID);

  @Override
  public SymbolTable visitProgram(Program<? extends Nameable> that) {
    SymbolTable symtab = new SymbolTable();
    symtab.enterScope();
    // basic types are just there
    for (BasicType b : BASIC_TYPES) {
      symtab.insert(b.name(), b);
    }
    for (Class<? extends Nameable> c : that.declarations) {
      Optional<Definition> sameType = symtab.lookup(c.name());
      if (sameType.isPresent()) {
        throw new SemanticError(
            "Type with name "
                + c.name()
                + "(defined at "
                + c.range()
                + ") is already defined at "
                + sameType.get().range());
      }
      symtab.insert(c.name(), c);
    }
    return symtab;
  }
}
