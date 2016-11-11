package minijava.util;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import java.io.StringWriter;
import minijava.ast.Program;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class PrettyPrinterProperties {

  @Property(trials = 800)
  public void shouldBeIdempotent(
      @From(ProgramGenerator.class) @Size(max = 1500) GeneratedProgram p) {
    StringWriter out = new StringWriter();
    PrettyPrinter<String> prettyPrinter = new PrettyPrinter<>(out);
    p.program.acceptVisitor(prettyPrinter);
    String expected = out.toString();

    Program<String> parsed = new Parser(new Lexer(expected)).parse();

    out = new StringWriter();
    prettyPrinter = new PrettyPrinter<>(out);
    parsed.acceptVisitor(prettyPrinter);
    String actual = out.toString();

    Assert.assertEquals(expected, actual);
  }
}
