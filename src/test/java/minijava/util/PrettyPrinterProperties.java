package minijava.util;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.Size;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import difflib.DiffUtils;
import difflib.Patch;
import java.util.Arrays;
import java.util.List;
import minijava.ast.Program;
import minijava.lexer.Lexer;
import minijava.parser.Parser;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.runner.RunWith;

@RunWith(JUnitQuickcheck.class)
public class PrettyPrinterProperties {

  private static final PrettyPrinter<String> PRETTY_PRINTER = new PrettyPrinter<>();

  @Property(trials = 800)
  public void shouldBeIdempotent(
      @From(ProgramGenerator.class) @Size(max = 1500) GeneratedProgram p) {

    String expected = p.program.acceptVisitor(PRETTY_PRINTER).toString();

    // Debug printfs:
    System.out.println("expected:");
    System.out.println(expected);

    Program<String> parsed = new Parser(new Lexer(expected)).parse();

    String actual = parsed.acceptVisitor(PRETTY_PRINTER).toString();

    // Debug printfs:
    System.out.println("actual:");
    System.out.println(actual);

    Patch<String> patch = DiffUtils.diff(listOfLines(expected), listOfLines(actual));
    System.out.println("diff:");
    patch
        .getDeltas()
        .forEach(
            d -> {
              System.out.println(
                  String.format(
                      "[%04d]: ---- %s ++++ %s",
                      d.getOriginal().getPosition(),
                      d.getOriginal().getLines(),
                      d.getRevised().getLines()));
            });

    Assert.assertEquals(expected, actual);
  }

  @NotNull
  private static List<String> listOfLines(String expected) {
    return Arrays.asList(expected.split("\n"));
  }
}
