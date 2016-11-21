package minijava.semantic;

import static com.github.npathai.hamcrestopt.OptionalMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import minijava.ast.Definition;
import minijava.ast.Field;
import minijava.ast.Ref;
import minijava.ast.Type;
import minijava.util.SourceRange;
import org.junit.Before;
import org.junit.Test;

public class SymbolTableTest {

  SymbolTable<Definition> symtab;

  private static final Definition SOME_DEFINITION =
      new Field(
          new Type(new Ref("int"), 0, SourceRange.FIRST_CHAR), "myName", SourceRange.FIRST_CHAR);

  @Before
  public void setUp() {
    symtab = new SymbolTable<>();
  }

  @Test(expected = IllegalStateException.class)
  public void leaveScope_notInAScope_throws() throws Exception {
    symtab.leaveScope();
  }

  @Test(expected = IllegalStateException.class)
  public void insert_notInAScope_throws() throws Exception {
    symtab.insert("myname", SOME_DEFINITION);
  }

  @Test
  public void lookup_notInAScope_noResult() throws Exception {
    assertThat(symtab.lookup("myName"), isEmpty());
  }

  @Test
  public void lookup_inScopeButNameNotVisible_noResult() throws Exception {
    symtab.enterScope();
    assertThat(symtab.lookup("myName"), isEmpty());
  }

  @Test
  public void lookup_nameIsVisible_returnsDefinition() throws Exception {
    symtab.enterScope();
    symtab.insert("myName", SOME_DEFINITION);
    assertThat(symtab.lookup("myName"), allOf(isPresent(), hasValue(SOME_DEFINITION)));
  }

  @Test
  public void inCurrentScopeStory() throws Exception {
    symtab.enterScope();
    symtab.insert("myName", SOME_DEFINITION);
    // myName is visible
    assertThat(symtab.lookup("myName"), allOf(isPresent(), hasValue(SOME_DEFINITION)));
    // and it is in current scope
    assertThat(symtab.inCurrentScope("myName"), is(true));

    symtab.enterScope();
    // after entering scope, name defined in parent scope is still visible
    assertThat(symtab.lookup("myName"), allOf(isPresent(), hasValue(SOME_DEFINITION)));
    // but it is _not_ in the current scope
    assertThat(symtab.inCurrentScope("myName"), is(false));

    symtab.leaveScope();
    // after leaving the new scope, name is visible
    assertThat(symtab.lookup("myName"), allOf(isPresent(), hasValue(SOME_DEFINITION)));
    // and it _is_ in current scope again
    assertThat(symtab.inCurrentScope("myName"), is(true));
  }
}
