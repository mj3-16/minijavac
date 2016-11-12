package minijava.util;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import minijava.ast.Program;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class PrettyPrinterProperties {

  private static final PrettyPrinter<String> PRETTY_PRINTER = new PrettyPrinter<>();

  @Property(trials = 800)
  public void shouldBeIdempotent(
      @From(ProgramGenerator.class) @Size(max = 1500) GeneratedProgram p) {

    String expected = p.program.acceptVisitor(PRETTY_PRINTER).toString();

    Program<String> parsed = new Parser(new Lexer(expected)).parse();

    String actual = parsed.acceptVisitor(PRETTY_PRINTER).toString();

    // Debug printfs:
    System.out.println("expected:");
    System.out.println(expected);
    System.out.println("actual:");
    System.out.println(actual);

    Assert.assertEquals(expected, actual);
  }
}
