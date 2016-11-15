package minijava.semantic;

import static org.junit.Assert.*;

import com.google.common.collect.ImmutableList;
import minijava.ast.*;
import minijava.ast.Class;
import minijava.util.SourcePosition;
import minijava.util.SourceRange;
import org.junit.Test;

public class NameAnalyzerTest {

  static final SourceRange SOME_RANGE = new SourceRange(new SourcePosition(1, 0), 1);

  @Test
  public void sameClass() throws Exception {

    Class<Nameable> intClass =
        new Class<>("int", ImmutableList.of(), ImmutableList.of(), SOME_RANGE);
    Class<Nameable> intClass2 =
        new Class<>("int", ImmutableList.of(), ImmutableList.of(), SOME_RANGE);
    Program<Nameable> p = new Program<>(ImmutableList.of(intClass, intClass2), SOME_RANGE);
    p.acceptVisitor(new NameAnalyzer());
  }
}
