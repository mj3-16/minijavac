package minijava.semantic;

import static com.google.common.base.Preconditions.checkState;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import minijava.ast.Definition;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

class SymbolTable<T extends Definition> {
  // a new SymbolTable is initialized with an empty scope
  private Scope<T> current = null;

  void enterScope() {
    if (current == null) {
      // outer most scope, has no parent and inherits no definitions
      current = new Scope<>(null, HashTreePMap.empty());
    } else {
      current = new Scope<>(current, current.allVisibleDefs);
    }
  }

  /**
   * @throws IllegalStateException if currently not in a scope (e.g. after creating a new instance
   *     of this class)
   */
  void leaveScope() {
    checkState(current != null, "You are not in a scope currently, so you can't leave it!");
    current = current.parent;
  }

  /**
   * This method possibly overwrites names defined in the current scope. If this is forbidden, check
   * with {@link #inCurrentScope(String)} first.
   *
   * @throws IllegalStateException if currently not in a scope (e.g. after creating a new instance
   *     of this class)
   */
  void insert(String name, T def) {
    checkState(
        current != null,
        "You must be in a scope, if you want to insert things. Call enterScope() first.");
    current.insert(name, def);
  }

  /**
   * Lookup {@code name} in current and all parent scopes and return the definition closest to the
   * current position, or {@link Optional#empty()} if {@code name} was not defined.
   */
  Optional<T> lookup(String name) {
    if (current == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(current.lookup(name));
  }

  /** Returns true if the given {@code name} was defined in the current scope */
  boolean inCurrentScope(String name) {
    return current.inScope(name);
  }

  private static class Scope<T extends Definition> {
    private final Scope<T> parent;
    private final Set<String> defsInScope = new HashSet<>();
    private PMap<String, T> allVisibleDefs;

    private Scope(Scope<T> parent, PMap<String, T> visibleDefs) {
      this.parent = parent;
      this.allVisibleDefs = visibleDefs;
    }

    private T lookup(String name) {
      return allVisibleDefs.get(name);
    }

    private void insert(String name, T def) {
      defsInScope.add(name);
      allVisibleDefs = allVisibleDefs.plus(name, def);
    }

    private boolean inScope(String name) {
      return defsInScope.contains(name);
    }
  }
}
